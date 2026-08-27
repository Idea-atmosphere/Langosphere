package com.example.logic

import android.util.Log
import org.bouncycastle.crypto.digests.RIPEMD128Digest
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import kotlin.math.min

import java.util.zip.Inflater

/**
 * Complete MDict reader ported from Ciyue dict_reader (Dart).
 * Supports MDX (dictionary) and MDD (resource) files.
 * Handles: header parsing, key blocks, record blocks, encryption (RIPEMD128+XOR),
 * stylesheet substitution, @@LINK= redirects, and on-demand record reading.
 */
class MdictReader(private val filePath: String) {

    companion object {
        private const val TAG = "MdictReader"
    }

    private var raf: RandomAccessFile? = null
    private var isMdx: Boolean = true

    // Header info
    var header = mutableMapOf<String, String>()
        private set
    var encoding = "UTF-8"
        private set
    private var encrypt = 0
    private var version = 1.0
    private var numberWidth = 4

    // Key block
    private var keyBlockOffset = 0L
    private var recordBlockOffset = 0L
    private var keyList = mutableListOf<ParsedKey>()
    var numEntries = 0L
        private set

    // Record block info
    private var recordBlockInfoList = mutableListOf<Pair<Long, Long>>() // (compressedSize, decompressedSize)
    private var totalDecompressedSize = 0L

    // Stylesheet
    private val stylesheet = mutableMapOf<String, Pair<String, String>>()

    // State
    var isInitialized = false
        private set
    var isLoading = true
        private set

    data class ParsedKey(val recordOffset: Long, val word: String) : Comparable<ParsedKey> {
        override fun compareTo(other: ParsedKey): Int {
            val cmp = recordOffset.compareTo(other.recordOffset)
            return if (cmp != 0) cmp else word.compareTo(other.word)
        }
    }

    data class RecordOffsetInfo(
        val key: String,
        val recordBlockFileOffset: Long,
        val startOffset: Int,
        val endOffset: Int,
        val compressedSize: Long
    )

    fun open() {
        raf = RandomAccessFile(filePath, "r")
        isMdx = filePath.endsWith(".mdx", ignoreCase = true)
    }

    fun close() {
        raf?.close()
        raf = null
    }

    /**
     * Initialize the dictionary: read header, optionally read keys and record block info.
     * @param maxKeys Maximum number of keys to load (0 = unlimited)
     */
    fun initDict(
        readHeader: Boolean = true,
        readKeys: Boolean = true,
        readRecordBlockInfo: Boolean = true,
        maxKeys: Int = 0
    ) {
        val f = raf ?: throw IllegalStateException("File not opened")

        try {
            if (readHeader) {
                Log.d(TAG, "initDict: reading header...")
                readHeader(f)
                Log.d(TAG, "initDict: header done, keyBlockOffset=$keyBlockOffset")
            }

            if (readKeys) {
                Log.d(TAG, "initDict: reading keys with maxKeys=$maxKeys...")
                readKeys(f, maxKeys)
                Log.d(TAG, "initDict: keys done")
                if (readRecordBlockInfo) {
                    Log.d(TAG, "initDict: reading record block info...")
                    readRecordBlockInfo(f)
                    Log.d(TAG, "initDict: record block info done")
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "initDict: OOM during initialization!", e)
            keyList.clear()
            throw e
        }

        isInitialized = true
        isLoading = false
    }

    // ── Header ────────────────────────────────────────────────────────────

    // File endianness - detected from header
    private var fileBigEndian = false

    private fun readHeader(f: RandomAccessFile) {
        f.seek(0)
        val fileSize = f.length()

        // Read first 4 bytes both ways to detect endianness
        val buf = ByteArray(4)
        f.readFully(buf)
        
        // Log raw bytes in hex for debugging
        val hexString = buf.joinToString("") { "%02x".format(it) }
        Log.d(TAG, "readHeader: Raw bytes: $hexString")
        
        f.seek(0) // reset
        val le = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
        val be = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
        Log.d(TAG, "readHeader: first 4 bytes: LE=$le (0x${Integer.toHexString(le)}), BE=$be (0x${Integer.toHexString(be)}), fileSize=$fileSize")

        val headerBytesSize: Int
        if (be > 0 && be < fileSize && (le <= 0 || le >= fileSize)) {
            headerBytesSize = be
            fileBigEndian = true
            Log.d(TAG, "readHeader: using BIG-ENDIAN, headerBytesSize=$headerBytesSize")
        } else if (le > 0 && le < fileSize) {
            headerBytesSize = le
            fileBigEndian = false
            Log.d(TAG, "readHeader: using LITTLE-ENDIAN, headerBytesSize=$headerBytesSize")
        } else {
            Log.e(TAG, "readHeader: both LE=$le and BE=$be are invalid for fileSize=$fileSize")
            return
        }

        // Now read the header content
        f.seek(4) // skip the 4 bytes we already read
        val contentBytes = ByteArray(headerBytesSize)
        f.readFully(contentBytes)
        keyBlockOffset = headerBytesSize.toLong() + 8
        Log.d(TAG, "readHeader: keyBlockOffset=$keyBlockOffset")

        // Detect encoding: if ends with double null, it's UTF-16LE
        val content = if (contentBytes.size >= 2 &&
            contentBytes[contentBytes.size - 1] == 0.toByte() &&
            contentBytes[contentBytes.size - 2] == 0.toByte()
        ) {
            String(contentBytes, 0, contentBytes.size - 2, Charset.forName("UTF-16LE"))
        } else {
            String(contentBytes, 0, contentBytes.size - 1, Charsets.UTF_8)
        }

        header = parseHeader(content)
        Log.d(TAG, "readHeader: parsed header tags: ${header.keys}")

        // Encoding
        val enc = header["Encoding"]
        encoding = when {
            enc.isNullOrEmpty() -> if (isMdx) "UTF-8" else "UTF-16"
            enc == "GBK" || enc == "GB2312" -> "GB18030"
            else -> enc
        }

        // Encryption
        encrypt = when {
            !header.containsKey("Encrypted") || header["Encrypted"] == "No" -> 0
            header["Encrypted"] == "Yes" -> 1
            else -> header["Encrypted"]?.toIntOrNull() ?: 0
        }

        // Stylesheet
        header["StyleSheet"]?.let { parseStylesheet(it) }

        // Version - CRITICAL: must parse correctly or numberWidth will be wrong
        val versionStr = header["GeneratedByEngineVersion"]
        Log.d(TAG, "readHeader: GeneratedByEngineVersion='$versionStr'")
        version = versionStr?.toDoubleOrNull() ?: 1.0
        numberWidth = if (version < 2.0) 4 else 8
        Log.d(TAG, "readHeader: version=$version, numberWidth=$numberWidth, encoding=$encoding, encrypt=$encrypt")

        // Version 3.0+ uses UTF-8 only
        if (version >= 3.0) {
            encoding = "UTF-8"
        }
    }

    private fun parseHeader(content: String): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)="(.*?)"""", RegexOption.DOT_MATCHES_ALL)
        for (match in regex.findAll(content)) {
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun parseStylesheet(raw: String) {
        val lines = raw.lines()
        var i = 0
        while (i + 2 < lines.size) {
            val key = lines[i].trim()
            val begin = htmlUnescape(lines[i + 1].trim())
            val end = htmlUnescape(lines[i + 2].trim())
            stylesheet[key] = Pair(begin, end)
            i += 3
        }
        Log.d(TAG, "Stylesheet: ${stylesheet.size} entries")
    }

    private fun htmlUnescape(s: String): String {
        return s
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    // ── Keys ──────────────────────────────────────────────────────────────

    /**
     * Read keys in streaming fashion: decompress one key block at a time.
     * Stops after maxKeys (0 = unlimited). Avoids loading entire key block into memory.
     */
    private fun readKeys(f: RandomAccessFile, maxKeys: Int = 0) {
        f.seek(keyBlockOffset)
        Log.d(TAG, "readKeys: seeked to keyBlockOffset=$keyBlockOffset")

        // number of key blocks
        val numKeyBlocks = readNumber(f)
        // number of entries
        numEntries = readNumber(f)
        Log.d(TAG, "readKeys: numKeyBlocks=$numKeyBlocks, numEntries=$numEntries")

        // version 2+: decompressed key block info size
        if (version >= 2.0) {
            val decompressedInfoSize = readNumber(f)
            Log.d(TAG, "readKeys: decompressedKeyBlockInfoSize=$decompressedInfoSize")
        }

        // key block info size
        val keyBlockInfoSize = readNumber(f)
        // key block size
        val keyBlockSize = readNumber(f)
        Log.d(TAG, "readKeys: keyBlockInfoSize=$keyBlockInfoSize, keyBlockSize=$keyBlockSize")

        // Safety check - if sizes are unreasonably large, something is wrong
        if (keyBlockInfoSize > 100_000_000) { // 100MB max for info
            throw IllegalStateException("keyBlockInfoSize=$keyBlockInfoSize is too large (>100MB)")
        }
        if (keyBlockSize > 50_000_000L) { // 50MB max for streaming key blocks
            throw IllegalStateException("keyBlockSize=$keyBlockSize is too large for streaming (>50MB)")
        }

        // version 2+: skip 4 bytes
        if (version >= 2.0) {
            f.skipBytes(4) // Dart: await f.read(4) — exactly 4 bytes, NOT numberWidth!
        }

        Log.d(TAG, "readKeys: about to allocate keyBlockInfoBytes of ${keyBlockInfoSize.toInt()} bytes")
        // Read and decode key block info (small metadata)
        val keyBlockInfoBytes = ByteArray(keyBlockInfoSize.toInt())
        f.readFully(keyBlockInfoBytes)
        Log.d(TAG, "readKeys: keyBlockInfoBytes read, decoding...")
        val keyBlockInfoList = decodeKeyBlockInfo(keyBlockInfoBytes)
        Log.d(TAG, "readKeys: keyBlockInfoList decoded, ${keyBlockInfoList.size} blocks")

        // Skip past the key blocks to get to record block offset
        val keyBlockStartPos = f.filePointer

        // Read key blocks ONE AT A TIME (streaming, no OOM)
        keyList.clear()
        for ((idx, compressedSize) in keyBlockInfoList.withIndex()) {
            // Safety: skip absurdly large blocks
            if (compressedSize > 50_000_000) {
                Log.e(TAG, "readKeys: block $idx has absurd compressedSize=$compressedSize, skipping")
                continue
            }

            // Read compressed data for this single block
            val blockCompressed = ByteArray(compressedSize.toInt())
            f.readFully(blockCompressed)

            // Decompress this single block
            val decompressed = decodeBlock(blockCompressed)
            val blockKeys = splitKeyBlock(decompressed)

            // Add keys until we hit the limit
            for (key in blockKeys) {
                keyList.add(key)
                if (maxKeys > 0 && keyList.size >= maxKeys) {
                    Log.d(TAG, "readKeys: reached maxKeys=$maxKeys, stopping at block $idx")
                    break
                }
            }

            if (maxKeys > 0 && keyList.size >= maxKeys) break

            // Release memory for this block
            blockCompressed.fill(0)

            if (idx % 50 == 0) {
                Log.d(TAG, "readKeys: block ${idx + 1}/${keyBlockInfoList.size}: ${keyList.size} keys")
            }
        }

        // Record block offset = after all key blocks
        recordBlockOffset = keyBlockStartPos + keyBlockSize

        // Sort by word for binary search
        keyList.sortBy { it.word }

        Log.d(TAG, "readKeys: DONE. ${keyList.size} keys loaded (numEntries=$numEntries)")
    }

    // ── Key Block Info ────────────────────────────────────────────────────

    private fun decodeKeyBlockInfo(compressed: ByteArray): List<Long> {
        val infoList = mutableListOf<Long>()
        var data = compressed

        Log.d(TAG, "decodeKeyBlockInfo: input size=${compressed.size}, version=$version, encrypt=$encrypt")

        if (version >= 2.0) {
            if (encrypt == 2) {
                // RIPEMD128 decryption
                val keyMaterial = ByteArray(8)
                System.arraycopy(compressed, 4, keyMaterial, 0, 4)
                val constant = byteArrayOf(149.toByte(), 54, 0, 0)
                val hashInput = keyMaterial.copyOf(4) + constant
                val key = ripemd128(hashInput)

                val headerPart = compressed.copyOf(8)
                val encryptedPart = compressed.copyOfRange(8, compressed.size)
                val decrypted = xorDecrypt(encryptedPart, key)
                data = headerPart + decrypted
                Log.d(TAG, "decodeKeyBlockInfo: decrypted, size=${data.size}")
            }

            // Skip 8-byte header, decompress the rest
            val payload = data.copyOfRange(8, data.size)
            Log.d(TAG, "decodeKeyBlockInfo: decompressing payload of ${payload.size} bytes")
            data = zlibDecompress(payload)
            Log.d(TAG, "decodeKeyBlockInfo: decompressed to ${data.size} bytes")

            // Safety check
            if (data.size > 50_000_000) {
                Log.e(TAG, "decodeKeyBlockInfo: decompressed size ${data.size} is too large!")
            }
        }

        // Parse the key block info entries
        var i = 0
        val byteWidth = if (version >= 2.0) 2 else 1
        val textTerm = if (version >= 2.0) 1 else 0

        while (i < data.size) {
            // record size (numberWidth)
            i += numberWidth

            // text head size
            val textHeadSize = readByteOrShort(data, i, byteWidth)
            i += byteWidth

            // text head
            i += if (encoding == "UTF-16") {
                (textHeadSize + textTerm) * 2
            } else {
                textHeadSize + textTerm
            }

            // text tail size
            val textTailSize = readByteOrShort(data, i, byteWidth)
            i += byteWidth

            // text tail
            i += if (encoding == "UTF-16") {
                (textTailSize + textTerm) * 2
            } else {
                textTailSize + textTerm
            }

            // key block compressed size
            val keyBlockCompressedSize = readNumberFromArray(data, i, numberWidth)
            i += numberWidth

            // key block decompressed size (skip)
            i += numberWidth

            infoList.add(keyBlockCompressedSize)
        }

        return infoList
    }

    private fun readByteOrShort(data: ByteArray, offset: Int, width: Int): Int {
        return if (width == 1) {
            data[offset].toInt() and 0xFF
        } else {
            // Dart ByteData.getUint16() defaults to Big Endian
            ByteBuffer.wrap(data, offset, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        }
    }

    // ── Key Block Decoding ────────────────────────────────────────────────

    private fun decodeKeyBlock(
        keyBlockCompressed: ByteArray,
        keyBlockInfoList: List<Long>
    ): MutableList<ParsedKey> {
        val result = mutableListOf<ParsedKey>()
        var pos = 0
        for (compressedSize in keyBlockInfoList) {
            val block = keyBlockCompressed.copyOfRange(pos, pos + compressedSize.toInt())
            val decompressed = decodeBlock(block)
            result.addAll(splitKeyBlock(decompressed))
            pos += compressedSize.toInt()
        }
        return result
    }

    private fun splitKeyBlock(keyBlock: ByteArray): List<ParsedKey> {
        val result = mutableListOf<ParsedKey>()
        var i = 0
        val charset = getCharset()

        while (i < keyBlock.size) {
            // Record offset (numberWidth bytes)
            val recordOffset = readNumberFromArray(keyBlock, i, numberWidth)
            i += numberWidth

            // Find null terminator
            val nullWidth = if (encoding == "UTF-16") 2 else 1
            var end = i
            while (end < keyBlock.size - nullWidth + 1) {
                if (nullWidth == 2) {
                    if (keyBlock[end] == 0.toByte() && keyBlock[end + 1] == 0.toByte()) break
                    end += 2
                } else {
                    if (keyBlock[end] == 0.toByte()) break
                    end++
                }
            }

            if (end > i) {
                var word = String(keyBlock, i, end - i, charset)
                if (!isMdx) {
                    // MDD: normalize backslashes to forward slashes
                    word = word.replace("\\", "/")
                    if (word.startsWith("/")) word = word.substring(1)
                }
                result.add(ParsedKey(recordOffset, word))
            }
            i = end + nullWidth
        }
        return result
    }

    // ── Record Block Info ─────────────────────────────────────────────────

    private fun readRecordBlockInfo(f: RandomAccessFile) {
        f.seek(recordBlockOffset)

        val numRecordBlocks = readNumber(f)
        readNumber(f) // number of entries
        readNumber(f) // size of record block info
        readNumber(f) // size of record block

        recordBlockInfoList.clear()
        totalDecompressedSize = 0
        for (i in 0 until numRecordBlocks) {
            val compressedSize = readNumber(f)
            val decompressedSize = readNumber(f)
            recordBlockInfoList.add(Pair(compressedSize, decompressedSize))
            totalDecompressedSize += decompressedSize
        }

        Log.d(TAG, "Record blocks: ${recordBlockInfoList.size}, totalDecompressed=$totalDecompressedSize")
    }

    // ── Locate (single entry) ────────────────────────────────────────────

    /**
     * Locate a single entry by key. Returns null if not found.
     */
    fun locate(key: String): RecordOffsetInfo? {
        val idx = binarySearchByKey(key)
        if (idx < 0 || keyList[idx].word != key) return null
        return buildRecordOffsetInfo(idx)
    }

    /**
     * Locate ALL entries with the same key (e.g., "test" as noun + "test" as verb).
     */
    fun locateAll(key: String): List<RecordOffsetInfo> {
        val results = mutableListOf<RecordOffsetInfo>()

        // Find first match using lower bound
        var idx = lowerBound(key)
        if (idx >= keyList.size || keyList[idx].word != key) return results

        // Collect all matching entries
        while (idx < keyList.size && keyList[idx].word == key) {
            buildRecordOffsetInfo(idx)?.let { results.add(it) }
            idx++
        }

        Log.d(TAG, "locateAll('$key'): found ${results.size} entries")
        return results
    }

    private fun buildRecordOffsetInfo(idx: Int): RecordOffsetInfo? {
        val recordStart = keyList[idx].recordOffset
        val recordEnd = if (idx < keyList.size - 1) {
            keyList[idx + 1].recordOffset
        } else {
            totalDecompressedSize
        }

        // Find the correct record block
        var accumulatedDecompressedSize = 0L
        var recordBlockFileOffset = recordBlockOffset + numberWidth * 4 +
            recordBlockInfoList.size * numberWidth * 2

        for ((compressedSize, decompressedSize) in recordBlockInfoList) {
            if (recordStart < accumulatedDecompressedSize + decompressedSize) {
                val startOffset = (recordStart - accumulatedDecompressedSize).toInt()
                var endOffset = (recordEnd - accumulatedDecompressedSize).toInt()
                if (endOffset > decompressedSize.toInt()) {
                    endOffset = decompressedSize.toInt()
                }
                return RecordOffsetInfo(
                    keyList[idx].word,
                    recordBlockFileOffset,
                    startOffset,
                    endOffset,
                    compressedSize
                )
            }
            accumulatedDecompressedSize += decompressedSize
            recordBlockFileOffset += compressedSize
        }
        return null
    }

    // ── Read single MDX record ────────────────────────────────────────────

    /**
     * Read a single MDX record by its offset info.
     * Handles encoding and stylesheet substitution.
     */
    fun readOneMdx(info: RecordOffsetInfo): String {
        val f = raf ?: throw IllegalStateException("File not opened")
        f.seek(info.recordBlockFileOffset)

        val compressed = ByteArray(info.compressedSize.toInt())
        f.readFully(compressed)
        val decompressed = decodeBlock(compressed)

        val data = decompressed.copyOfRange(info.startOffset, min(info.endOffset, decompressed.size))
        return treatRecordMdxData(data)
    }

    /**
     * Read a single MDD record (binary data).
     */
    fun readOneMdd(info: RecordOffsetInfo): ByteArray {
        val f = raf ?: throw IllegalStateException("File not opened")
        f.seek(info.recordBlockFileOffset)

        val compressed = ByteArray(info.compressedSize.toInt())
        f.readFully(compressed)
        val decompressed = decodeBlock(compressed)

        return decompressed.copyOfRange(info.startOffset, min(info.endOffset, decompressed.size))
    }

    // ── Search (prefix match) ─────────────────────────────────────────────

    /**
     * Search for keys starting with the given prefix.
     */
    fun search(key: String, limit: Int = 30): List<String> {
        val results = mutableListOf<String>()
        val idx = lowerBound(key)
        for (i in idx until min(idx + limit * 5, keyList.size)) {
            if (results.size >= limit) break
            if (keyList[i].word.startsWith(key, ignoreCase = true)) {
                results.add(keyList[i].word)
            } else {
                break
            }
        }
        return results
    }

    /**
     * Check if a key exists.
     */
    fun exist(key: String): Boolean {
        val idx = binarySearchByKey(key)
        return idx >= 0 && keyList[idx].word == key
    }

    // ── Record Data Processing ────────────────────────────────────────────

    private fun treatRecordMdxData(data: ByteArray): String {
        val charset = getCharset()
        var result = String(data, charset)

        if (stylesheet.isNotEmpty()) {
            result = substituteStylesheet(result)
        }

        return result
    }

    private fun substituteStylesheet(txt: String): String {
        val regExp = Regex("""`\d+`""")
        val txtList = regExp.split(txt)
        val txtTags = regExp.findAll(txt).map { it.value }.toList()
        var result = txtList[0]

        for (j in txtTags.indices) {
            val p = txtList.getOrElse(j + 1) { "" }
            val styleKey = txtTags[j].removeSurrounding("`")
            val style = stylesheet[styleKey]

            if (style != null) {
                if (p.isNotEmpty() && p.endsWith('\n')) {
                    result = "$result${style.first}${p.trimEnd()}${style.second}\r\n"
                } else {
                    result = "$result${style.first}$p${style.second}"
                }
            } else {
                result = "$result${txtTags[j]}$p"
            }
        }
        return result
    }

    /**
     * Get all keys sorted by word (for batch import).
     */
    fun getAllKeysSorted(): List<ParsedKey> {
        // Return a copy sorted by word
        return keyList.sortedBy { it.word }
    }

    // ── Binary Search Helpers ──────────────────────────────────────────────

    private fun binarySearchByKey(key: String): Int {
        var lo = 0
        var hi = keyList.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val cmp = keyList[mid].word.compareTo(key)
            if (cmp < 0) lo = mid + 1
            else if (cmp > 0) hi = mid - 1
            else return mid
        }
        return -1
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = keyList.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (keyList[mid].word.compareTo(key) < 0) lo = mid + 1
            else hi = mid
        }
        return lo
    }

    // ── Block Decoding ────────────────────────────────────────────────────

    private fun decodeBlock(block: ByteArray): ByteArray {
        if (block.size <= 8) return block

        val info = ByteBuffer.wrap(block, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val compressionMethod = info and 0xf
        val data = block.copyOfRange(8, block.size)

        return when (compressionMethod) {
            0 -> data
            2 -> zlibDecompress(data)
            else -> throw IllegalArgumentException("Compression method $compressionMethod not supported")
        }
    }

    private fun zlibDecompress(compressed: ByteArray): ByteArray {
        val decompressor = Inflater()
        decompressor.setInput(compressed, 0, compressed.size)
        val bos = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        try {
            while (!decompressor.finished()) {
                val count = decompressor.inflate(buf)
                if (count == 0) break
                bos.write(buf, 0, count)
            }
        } finally {
            decompressor.end()
        }
        return bos.toByteArray()
    }

    // ── Encryption ────────────────────────────────────────────────────────

    private fun xorDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        var previous = 0x36
        for (i in data.indices) {
            val byte = data[i].toInt() and 0xFF
            // Swap nibbles
            val t = ((byte shr 4) or ((byte shl 4))) and 0xFF
            // Apply XOR with key, loop index, and previous byte
            val decrypted = (t xor previous xor (i and 0xFF) xor (key[i % key.size].toInt() and 0xFF)) and 0xFF
            previous = byte // ORIGINAL byte, NOT decrypted! (matches Ciyue Dart source)
            data[i] = decrypted.toByte()
        }
        return data
    }

    private fun ripemd128(input: ByteArray): ByteArray {
        val digest = RIPEMD128Digest()
        digest.update(input, 0, input.size)
        val output = ByteArray(16)
        digest.doFinal(output, 0)
        return output
    }

    // ── I/O Helpers ───────────────────────────────────────────────────────

    private fun getCharset(): Charset = when (encoding.uppercase()) {
        "UTF-16", "UTF-16LE" -> Charset.forName("UTF-16LE")
        "UTF-16BE" -> Charset.forName("UTF-16BE")
        "GB18030", "GBK", "GB2312" -> Charset.forName("GB18030")
        else -> Charsets.UTF_8
    }

    private fun readInt32(f: RandomAccessFile): Int {
        val buf = ByteArray(4)
        f.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    /**
     * Read 4-byte int, try LE first. If value > fileSize (unreasonable), try BE.
     * Some MDX files use big-endian for header fields.
     */
    private fun readInt32AutoEndian(f: RandomAccessFile, fileSize: Long): Int {
        val buf = ByteArray(4)
        f.readFully(buf)
        val le = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
        if (le > 0 && le < fileSize) return le
        val be = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
        Log.d(TAG, "readInt32AutoEndian: LE=$le, BE=$be (fileSize=$fileSize)")
        return if (be > 0 && be < fileSize) be else le
    }

    private fun readNumber(f: RandomAccessFile): Long {
        val buf = ByteArray(numberWidth)
        f.readFully(buf)
        // Always Big Endian: Dart _readNumber uses Endian.big for ALL files
        return if (numberWidth == 4) {
            ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        } else {
            ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).long
        }
    }

    private fun readNumberFromArray(data: ByteArray, offset: Int, width: Int): Long {
        // Always Big Endian: matches Ciyue _readNumber with Endian.big
        return if (width == 4) {
            ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        } else {
            ByteBuffer.wrap(data, offset, 8).order(ByteOrder.BIG_ENDIAN).long
        }
    }
}
