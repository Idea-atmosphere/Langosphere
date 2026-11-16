package com.example.mdict

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** One headword with the offset of its record in the concatenated record data. */
data class KeyEntry(val recordOffset: Long, val key: String)

/**
 * MDX/MDD reader, port of readmdict.py (mdict-utils).
 *
 * Format (all integers big-endian unless noted):
 * header (BE size + UTF-16LE XML + LE Adler32), key header, key info list,
 * key blocks, record header, record info list, record blocks. v1.2 uses
 * 4-byte numbers, v2.0+ uses 8-byte numbers. v3.0 uses typed blocks.
 *
 * Keys are loaded eagerly at open (like the reference); record blocks are
 * decoded lazily and kept in a small LRU cache, so a multi-hundred-MB
 * dictionary never sits in RAM whole. All file access is guarded by one
 * lock because RandomAccessFile is not thread-safe.
 */
abstract class MdictFile protected constructor(val file: File, val isMdd: Boolean) : Closeable {

    var version: Double = 2.0
        private set
    var title: String = file.nameWithoutExtension
        private set
    var caseSensitive: Boolean = true
        private set
    var numEntries: Int = 0
        protected set
    var headerAttrs: Map<String, String> = emptyMap()
        private set

    protected var encoding: Charset = Charsets.UTF_8
    protected var isUtf16: Boolean = false
    protected var encrypt: Int = 0
    protected var numWidth: Int = 8
    protected val encryptedKey: ByteArray? = null // No passcode API in the app.

    var keyList: List<KeyEntry> = emptyList()
        protected set

    private var recordFileOffsets = LongArray(0)
    private var recordCompSizes = LongArray(0)
    private var recordDecompSizes = LongArray(0)
    private var recordDecompStarts = LongArray(0)
    private var recordBlockValid = BooleanArray(0)
    private var totalDecompressed: Long = 0

    private var raf: RandomAccessFile? = null
    private val lock = Any()
    private val blockCache = object : LinkedHashMap<Int, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>): Boolean {
            return size > 8
        }
    }

    init {
        open()
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    private fun open() {
        val f = RandomAccessFile(file, "r")
        raf = f
        try {
            // ---- header ----
            val headerSize = f.readInt()
            if (headerSize <= 0 || headerSize > 1024 * 1024) throw corrupt()
            val headerBytes = ByteArray(headerSize)
            f.readFully(headerBytes)
            val headerAdler = readLE32(f)
            if (BlockCodec.adler32(headerBytes) != (headerAdler.toLong() and 0xffffffffL)) {
                throw corrupt()
            }
            val headerText = decodeHeader(headerBytes)
            headerAttrs = parseHeaderAttrs(headerText)

            // ---- encoding ----
            var encName = (headerAttrs["Encoding"] ?: "UTF-8").uppercase()
            if (encName == "GBK" || encName == "GB2312") encName = "GB18030"
            encoding = when {
                isMdd -> Charsets.UTF_16LE
                encName == "UTF-16" || encName == "UTF16" -> Charsets.UTF_16LE
                encName == "UTF-8" || encName == "UTF8" -> Charsets.UTF_8
                else -> try {
                    Charset.forName(encName)
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
            }
            isUtf16 = encoding == Charsets.UTF_16LE

            // ---- flags ----
            encrypt = when (val v = headerAttrs["Encrypted"]) {
                null, "No" -> 0
                "Yes" -> 1
                else -> v.toIntOrNull() ?: 0
            }
            caseSensitive = headerAttrs["KeyCaseSensitive"] != "No"
            title = headerAttrs["Title"] ?: file.nameWithoutExtension

            version = headerAttrs["GeneratedByEngineVersion"]?.toDoubleOrNull() ?: 2.0
            numWidth = if (version < 2.0) 4 else 8
            if (version >= 3.0 && !isMdd) {
                // Reference forces UTF-8 for v3 MDX; MDD keys stay UTF-16.
                encoding = Charsets.UTF_8
                isUtf16 = false
            }

            // ---- keys ----
            val keyBlockOffset = f.filePointer
            if (version >= 3.0) {
                readKeysV3(keyBlockOffset)
            } else if ((encrypt and 0x01) != 0 && encryptedKey == null) {
                readKeysBrutal(keyBlockOffset)
            } else {
                readKeysV1V2(keyBlockOffset)
            }
        } catch (e: MdictException) {
            closeQuietly(f)
            throw e
        } catch (e: Exception) {
            closeQuietly(f)
            throw MdictException(MdictException.Kind.CORRUPT)
        }
    }

    private fun decodeHeader(bytes: ByteArray): String {
        var b = bytes
        if (b.size >= 2 && b[b.size - 1] == 0.toByte() && b[b.size - 2] == 0.toByte()) {
            b = b.copyOfRange(0, b.size - 2)
        } else if (b.isNotEmpty() && b[b.size - 1] == 0.toByte()) {
            b = b.copyOfRange(0, b.size - 1)
        }
        val charset = when {
            b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_16LE
        }
        val stripBom = if (b.size >= 2 && (b[0] == 0xFF.toByte() || b[0] == 0xFE.toByte())) 2 else 0
        return decodeBytes(b.copyOfRange(stripBom, b.size), charset)
    }

    private fun parseHeaderAttrs(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val regex = Regex("""(\w+)="(?s)(.*?)"""", RegexOption.DOT_MATCHES_ALL)
        for (m in regex.findAll(text)) {
            val value = m.groupValues[2]
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
            out[m.groupValues[1]] = value
        }
        return out
    }

    // ------------------------------------------------------------------
    // Keys, v1/v2
    // ------------------------------------------------------------------

    private fun readKeysV1V2(offset: Long) {
        val f = raf!!
        f.seek(offset)
        val numBytes = if (version >= 2.0) 40 else 16
        val block = ByteArray(numBytes)
        f.readFully(block)
        var blockBytes = block
        if ((encrypt and 1) != 0) {
            val k = encryptedKey ?: throw MdictException(MdictException.Kind.LOCKED)
            blockBytes = Salsa20(k, rounds = 8).crypt(block)
        }
        val buf = ByteBuffer.wrap(blockBytes)
        val numKeyBlocks = readNumber(buf)
        numEntries = readNumber(buf).toInt()
        if (version >= 2.0) {
            readNumber(buf) // key block info decompressed size (unused)
        }
        val keyBlockInfoSize = readNumber(buf)
        val keyBlockSize = readNumber(buf)
        if (version >= 2.0) {
            val adler = f.readInt()
            if (BlockCodec.adler32(blockBytes) != (adler.toLong() and 0xffffffffL)) {
                throw corrupt()
            }
        }
        val keyBlockInfo = ByteArray(keyBlockInfoSize.toInt())
        f.readFully(keyBlockInfo)
        val infoList = decodeKeyBlockInfo(keyBlockInfo)
        if (numKeyBlocks.toInt() != infoList.size) throw corrupt()
        val keyBlockCompressed = ByteArray(keyBlockSize.toInt())
        f.readFully(keyBlockCompressed)
        keyList = decodeKeyBlocks(keyBlockCompressed, infoList)
        readRecordInfoV1V2(f.filePointer)
    }

    /** Encrypted preamble, no passcode: scan for the key-block-info marker. */
    private fun readKeysBrutal(offset: Long) {
        val f = raf!!
        f.seek(offset)
        val numBytes = if (version >= 2.0) 44 else 16
        f.skipBytes(numBytes)
        val marker = if (version >= 2.0) {
            byteArrayOf(0x02, 0, 0, 0)
        } else {
            byteArrayOf(0x01, 0, 0, 0)
        }
        val infoHead = ByteArray(8)
        f.readFully(infoHead)
        if (version >= 2.0 && !infoHead.copyOfRange(0, 4).contentEquals(marker)) {
            throw corrupt()
        }
        val infoBytes = ByteArrayOutputStream()
        infoBytes.write(infoHead)
        while (true) {
            val fpos = f.filePointer
            val chunk = ByteArray(1024)
            val n = f.read(chunk)
            if (n < 0) throw corrupt()
            val data = chunk.copyOfRange(0, n)
            val idx = indexOf(data, marker)
            if (idx >= 0) {
                infoBytes.write(data, 0, idx)
                f.seek(fpos + idx)
                break
            } else {
                infoBytes.write(data)
            }
        }
        val infoList = decodeKeyBlockInfo(infoBytes.toByteArray())
        val keyBlockSize = infoList.sumOf { it.first }
        val keyBlockCompressed = ByteArray(keyBlockSize.toInt())
        f.readFully(keyBlockCompressed)
        keyList = decodeKeyBlocks(keyBlockCompressed, infoList)
        numEntries = keyList.size
        readRecordInfoV1V2(f.filePointer)
    }

    // ------------------------------------------------------------------
    // Keys, v3
    // ------------------------------------------------------------------

    private fun readKeysV3(offset: Long) {
        val f = raf!!
        f.seek(offset)
        var recordDataOff = -1L
        var recordIndexOff = -1L
        var keyDataOff = -1L
        while (true) {
            val blockType = f.readInt()
            val blockSize = readNumberRaf(f)
            val blockOffset = f.filePointer
            when (blockType) {
                0x01000000 -> recordDataOff = blockOffset
                0x02000000 -> recordIndexOff = blockOffset
                0x03000000 -> keyDataOff = blockOffset
                0x04000000 -> { /* key index: not needed, keys carry offsets */ }
                else -> throw MdictException(MdictException.Kind.UNSUPPORTED, "unknown v3 block type")
            }
            f.seek(f.filePointer + blockSize)
            val probe = ByteArray(4)
            if (f.read(probe) < 4) break
            f.seek(f.filePointer - 4)
        }
        if (keyDataOff < 0 || recordDataOff < 0 || recordIndexOff < 0) throw corrupt()

        // Key data.
        f.seek(keyDataOff)
        val numKeyBlocks = f.readInt()
        readNumberRaf(f) // total size (unused)
        val keys = ArrayList<KeyEntry>()
        repeat(numKeyBlocks) {
            val decomp = f.readInt().toLong() and 0xffffffffL
            val comp = f.readInt().toLong() and 0xffffffffL
            val data = ByteArray(comp.toInt())
            f.readFully(data)
            keys.addAll(splitKeyBlock(BlockCodec.decodeBlock(data, decomp, version, encryptedKey)))
        }
        keyList = keys
        numEntries = keys.size

        // Record index (block sizes from the index keep offsets aligned).
        val index = readRecordIndexV3(recordIndexOff)

        // Record data section: scan block headers once, decode lazily later.
        f.seek(recordDataOff)
        val nBlocks = f.readInt()
        readNumberRaf(f) // num bytes (unused)
        recordFileOffsets = LongArray(nBlocks)
        recordCompSizes = LongArray(nBlocks)
        recordDecompSizes = LongArray(nBlocks)
        recordDecompStarts = LongArray(nBlocks)
        recordBlockValid = BooleanArray(nBlocks) { true }
        var decompPos = 0L
        for (j in 0 until nBlocks) {
            val decomp = f.readInt().toLong() and 0xffffffffL
            val comp = f.readInt().toLong() and 0xffffffffL
            val idxEntry = if (j < index.size) index[j] else null
            val idxDecomp = idxEntry?.second ?: decomp
            if (idxEntry != null && (comp + 8 != idxEntry.first || decomp != idxEntry.second)) {
                recordBlockValid[j] = false // Damaged block: skipped by the reference too.
            }
            recordFileOffsets[j] = f.filePointer
            recordCompSizes[j] = comp
            recordDecompSizes[j] = idxDecomp
            recordDecompStarts[j] = decompPos
            decompPos += idxDecomp
            f.seek(f.filePointer + comp)
        }
        totalDecompressed = decompPos
    }

    private fun readRecordIndexV3(offset: Long): List<Pair<Long, Long>> {
        val f = raf!!
        f.seek(offset)
        val n = f.readInt()
        readNumberRaf(f) // num bytes (unused)
        val index = ArrayList<Pair<Long, Long>>()
        repeat(n) {
            val decomp = f.readInt().toLong() and 0xffffffffL
            val comp = f.readInt().toLong() and 0xffffffffL
            val data = ByteArray(comp.toInt())
            f.readFully(data)
            val decoded = BlockCodec.decodeBlock(data, decomp, version, encryptedKey)
            if (decoded.size % 16 != 0) throw corrupt()
            var p = 0
            while (p < decoded.size) {
                index.add(be64(decoded, p) to be64(decoded, p + 8))
                p += 16
            }
        }
        return index
    }

    // ------------------------------------------------------------------
    // Key block info / key blocks
    // ------------------------------------------------------------------

    private fun decodeKeyBlockInfo(bytes: ByteArray): List<Pair<Long, Long>> {
        val info: ByteArray
        if (version >= 2.0) {
            if (bytes.size < 8) throw corrupt()
            var b = bytes
            if ((encrypt and 0x02) != 0) {
                val keyMaterial = bytes.copyOfRange(4, 8) + le32Bytes(0x3695)
                val key = Ripemd128.digest(keyMaterial)
                b = bytes.copyOfRange(0, 8) +
                    BlockCodec.fastDecrypt(bytes.copyOfRange(8, bytes.size), key)
            }
            // The reference reader asserts the info word is zlib (02 00 00 00)
            // and therefore rejects type-0 (uncompressed) key info, even
            // though writemdict produces it. Handle both: method 0 = raw,
            // method 2 = zlib. Header is LE info word + BE Adler32.
            val infoWord = BlockCodec.le32(b, 0)
            val method = infoWord and 0xf
            val adler = BlockCodec.be32(b, 4).toLong() and 0xffffffffL
            info = when (method) {
                0 -> b.copyOfRange(8, b.size)
                2 -> inflateZlib(b.copyOfRange(8, b.size))
                else -> throw MdictException(MdictException.Kind.UNSUPPORTED, "unknown key-block compression")
            }
            if (BlockCodec.adler32(info) != adler) throw corrupt()
        } else {
            info = bytes
        }
        val list = ArrayList<Pair<Long, Long>>()
        val byteWidth = if (version >= 2.0) 2 else 1
        val textTerm = if (version >= 2.0) 1L else 0L
        var i = 0
        while (i < info.size) {
            readNumber(info, i) // entries in this key block (counted below instead)
            i += numWidth
            val headSize = readUInt(info, i, byteWidth)
            i += byteWidth
            i += if (isUtf16) ((headSize + textTerm) * 2).toInt() else (headSize + textTerm).toInt()
            val tailSize = readUInt(info, i, byteWidth)
            i += byteWidth
            i += if (isUtf16) ((tailSize + textTerm) * 2).toInt() else (tailSize + textTerm).toInt()
            val comp = readNumber(info, i)
            i += numWidth
            val decomp = readNumber(info, i)
            i += numWidth
            list.add(comp to decomp)
        }
        return list
    }

    private fun decodeKeyBlocks(
        compressed: ByteArray,
        infoList: List<Pair<Long, Long>>
    ): List<KeyEntry> {
        val keys = ArrayList<KeyEntry>()
        var pos = 0
        for ((comp, decomp) in infoList) {
            val block = compressed.copyOfRange(pos, pos + comp.toInt())
            pos += comp.toInt()
            keys.addAll(splitKeyBlock(BlockCodec.decodeBlock(block, decomp, version, encryptedKey)))
        }
        return keys
    }

    private fun splitKeyBlock(block: ByteArray): List<KeyEntry> {
        val list = ArrayList<KeyEntry>()
        val width = if (isUtf16) 2 else 1
        var pos = 0
        while (pos < block.size) {
            val recordOffset = readNumber(block, pos)
            pos += numWidth
            var end = -1
            var i = pos
            while (i + width <= block.size) {
                val isDelim = if (isUtf16) {
                    block[i] == 0.toByte() && block[i + 1] == 0.toByte()
                } else {
                    block[i] == 0.toByte()
                }
                if (isDelim) {
                    end = i
                    break
                }
                i += width
            }
            if (end < 0) throw corrupt()
            val keyText = decodeBytes(block.copyOfRange(pos, end), encoding).trim()
            list.add(KeyEntry(recordOffset, keyText))
            pos = end + width
        }
        return list
    }

    // ------------------------------------------------------------------
    // Record info (v1/v2) and lazy record blocks
    // ------------------------------------------------------------------

    private fun readRecordInfoV1V2(offset: Long) {
        val f = raf!!
        f.seek(offset)
        val numRecordBlocks = readNumberRaf(f)
        val numEntries2 = readNumberRaf(f)
        if (numEntries2.toInt() != numEntries) throw corrupt()
        readNumberRaf(f) // record block info size (unused)
        readNumberRaf(f) // record block size (unused)
        val infos = ArrayList<Pair<Long, Long>>()
        repeat(numRecordBlocks.toInt()) {
            infos.add(readNumberRaf(f) to readNumberRaf(f))
        }
        recordFileOffsets = LongArray(infos.size)
        recordCompSizes = LongArray(infos.size)
        recordDecompSizes = LongArray(infos.size)
        recordDecompStarts = LongArray(infos.size)
        recordBlockValid = BooleanArray(infos.size) { true }
        var filePos = f.filePointer
        var decompPos = 0L
        for (i in infos.indices) {
            recordFileOffsets[i] = filePos
            recordCompSizes[i] = infos[i].first
            recordDecompSizes[i] = infos[i].second
            recordDecompStarts[i] = decompPos
            filePos += infos[i].first
            decompPos += infos[i].second
        }
        totalDecompressed = decompPos
    }

    private fun blockIndexForOffset(pos: Long): Int {
        // Largest i with recordDecompStarts[i] <= pos.
        var lo = 0
        var hi = recordDecompStarts.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (recordDecompStarts[mid] <= pos) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun getRecordBlock(index: Int): ByteArray {
        synchronized(lock) {
            blockCache[index]?.let { return it }
            if (!recordBlockValid[index]) return ByteArray(0)
            val f = raf ?: throw MdictException(MdictException.Kind.CLOSED)
            f.seek(recordFileOffsets[index])
            val data = ByteArray(recordCompSizes[index].toInt())
            f.readFully(data)
            val decoded = BlockCodec.decodeBlock(
                data, recordDecompSizes[index], version, encryptedKey
            )
            blockCache[index] = decoded
            return decoded
        }
    }

    /** Raw record bytes for [start, end) in the concatenated record data. */
    private fun getRecordRange(start: Long, end: Long): ByteArray {
        if (end <= start) return ByteArray(0)
        val out = ByteArrayOutputStream()
        var pos = start
        while (pos < end) {
            val bi = blockIndexForOffset(pos)
            val block = getRecordBlock(bi)
            val blockStart = recordDecompStarts[bi]
            val blockEnd = blockStart + block.size
            val sliceEnd = minOf(end, blockEnd)
            val from = (pos - blockStart).toInt().coerceIn(0, block.size)
            val to = (sliceEnd - blockStart).toInt().coerceIn(from, block.size)
            if (to > from) {
                out.write(block, from, to - from)
                pos = blockStart + to
            } else if (block.isEmpty()) {
                // Damaged (v3) block: skip its whole decompressed span.
                pos = blockStart + recordDecompSizes[bi]
            } else {
                pos = blockEnd
            }
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------
    // Public lookup API
    // ------------------------------------------------------------------

    /** Raw record bytes for an exact headword, or null. */
    fun lookupRecordBytes(key: String): ByteArray? {
        val idx = findKeyIndex(key) ?: return null
        return recordBytesForIndex(idx)
    }

    /**
     * Raw record bytes of EVERY entry stored under [key], in file order.
     * MDX files may repeat a headword (e.g. "test" as a noun and again as
     * a verb); [lookupRecordBytes] returns only one of them, this returns
     * all. Empty when the key is absent.
     */
    fun lookupRecordBytesAll(key: String): List<ByteArray> {
        val idx = findKeyIndex(key) ?: return emptyList()
        synchronized(lock) {
            val matchKey = keyList[idx].key
            var first = idx
            while (first > 0 && keyList[first - 1].key == matchKey) first--
            var last = idx
            while (last + 1 < keyList.size && keyList[last + 1].key == matchKey) last++
            val out = ArrayList<ByteArray>(last - first + 1)
            for (i in first..last) out.add(recordBytesForIndex(i))
            return out
        }
    }

    /** Every headword in file order (a view over [keyList]). */
    fun headwords(): List<String> = keyList.map { it.key }

    private fun recordBytesForIndex(idx: Int): ByteArray {
        val start = keyList[idx].recordOffset
        val end = if (idx + 1 < keyList.size) {
            keyList[idx + 1].recordOffset
        } else {
            totalDecompressed
        }
        return getRecordRange(start, end)
    }

    private fun findKeyIndex(key: String): Int? {
        synchronized(lock) {
            var lo = 0
            var hi = keyList.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val cmp = keyList[mid].key.compareTo(key)
                when {
                    cmp == 0 -> return mid
                    cmp < 0 -> lo = mid + 1
                    else -> hi = mid - 1
                }
            }
            // Binary search missed: the file's collation may differ from
            // Kotlin's String order (e.g. case-insensitive dictionaries).
            // Fall back to a linear scan, exact then case-insensitive.
            for (i in keyList.indices) {
                if (keyList[i].key == key) return i
            }
            if (!caseSensitive) {
                for (i in keyList.indices) {
                    if (keyList[i].key.equals(key, ignoreCase = true)) return i
                }
            }
            return null
        }
    }

    /** Prefix suggestions in file order, at most [limit]. */
    fun suggestions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        synchronized(lock) {
            // Lower bound: first key >= prefix.
            var lo = 0
            var hi = keyList.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (keyList[mid].key.compareTo(prefix) < 0) lo = mid + 1 else hi = mid
            }
            val out = ArrayList<String>()
            var i = lo
            while (i < keyList.size && out.size < limit) {
                val k = keyList[i].key
                if (k.startsWith(prefix, ignoreCase = !caseSensitive)) {
                    out.add(k)
                } else if (k.compareTo(prefix, ignoreCase = !caseSensitive) > 0) {
                    break
                }
                i++
            }
            return out
        }
    }

    override fun close() {
        synchronized(lock) {
            blockCache.clear()
            closeQuietly(raf)
            raf = null
        }
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private fun corrupt(): MdictException = MdictException(MdictException.Kind.CORRUPT)

    private fun closeQuietly(c: Closeable?) {
        try {
            c?.close()
        } catch (ignored: Exception) {
        }
    }

    private fun readLE32(f: RandomAccessFile): Int {
        val b = ByteArray(4)
        f.readFully(b)
        return BlockCodec.le32(b, 0)
    }

    private fun readNumberRaf(f: RandomAccessFile): Long {
        val b = ByteArray(numWidth)
        f.readFully(b)
        return readNumber(b, 0)
    }

    /** Big-endian unsigned number of [numWidth] bytes at [off]. */
    private fun readNumber(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until numWidth) {
            v = (v shl 8) or (b[off + i].toLong() and 0xff)
        }
        return v
    }

    private fun readNumber(buf: ByteBuffer): Long {
        return if (numWidth == 8) {
            buf.long
        } else {
            buf.int.toLong() and 0xffffffffL
        }
    }

    private fun readUInt(b: ByteArray, off: Int, width: Int): Long {
        var v = 0L
        for (i in 0 until width) {
            v = (v shl 8) or (b[off + i].toLong() and 0xff)
        }
        return v
    }

    private fun be64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (b[off + i].toLong() and 0xff)
        }
        return v
    }

    private fun le32Bytes(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value ushr 8).toByte(),
        (value ushr 16).toByte(),
        (value ushr 24).toByte()
    )

    private fun inflateZlib(data: ByteArray): ByteArray {
        val inflater = java.util.zip.Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } catch (e: Exception) {
            throw MdictException(MdictException.Kind.CORRUPT, "zlib inflate failed")
        } finally {
            inflater.end()
        }
    }

    protected fun decodeBytes(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
        decoder.onMalformedInput(CodingErrorAction.IGNORE)
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun indexOf(data: ByteArray, marker: ByteArray): Int {
        if (marker.isEmpty() || data.size < marker.size) return -1
        outer@ for (i in 0..data.size - marker.size) {
            for (j in marker.indices) {
                if (data[i + j] != marker[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

/** Dictionary (.mdx): records are text in the header encoding. */
class MdxFile internal constructor(file: File) : MdictFile(file, false) {

    fun lookupText(key: String): String? {
        val bytes = lookupRecordBytes(key) ?: return null
        return decodeBytes(bytes, encoding).trim('\u0000')
    }

    /** Text of every entry stored under [key] (see [lookupRecordBytesAll]). */
    fun lookupTextAll(key: String): List<String> =
        lookupRecordBytesAll(key).map { decodeBytes(it, encoding).trim('\u0000') }
}

/** Resource (.mdd): keys are UTF-16 paths, records are raw bytes. */
class MddFile internal constructor(file: File) : MdictFile(file, true) {

    fun lookupBytes(key: String): ByteArray? = lookupRecordBytes(key)
}
