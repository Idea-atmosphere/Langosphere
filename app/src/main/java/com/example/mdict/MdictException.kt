package com.example.mdict

/**
 * Parser failure. Carries a [kind] the UI maps to a localized message
 * (EN/FA via AppStrings) and an English [detail] for logs. Never carries
 * a raw file path, so it is safe to surface to the user as-is.
 */
class MdictException(val kind: Kind, detail: String? = null) :
    Exception(detail?.let { "${kind.name}: $it" } ?: kind.name) {

    enum class Kind {
        /** Bad header/checksum/block layout, or an unreadable file. */
        CORRUPT,
        /** Encrypted with a passcode the app cannot ask for. */
        LOCKED,
        /** LZO compression, an unknown cipher, or an unknown v3 block type. */
        UNSUPPORTED,
        /** Lookup after [MdictFile.close]. */
        CLOSED,
    }
}
