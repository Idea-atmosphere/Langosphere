package com.example.logic

/**
 * A minimal MAJOR.MINOR.PATCH version value plus the parsing and ordering
 * rules the in-app updater needs to compare a GitHub release tag against
 * [com.example.BuildConfig.VERSION_NAME].
 *
 * Only the three-number core is modelled. Pre-release/build-metadata
 * suffixes ("-beta.1+build5") are intentionally ignored because the
 * repository's Release workflow only ever publishes tags that match
 * MAJOR.MINOR.PATCH exactly (see .github/workflows/release.yml), so
 * anything more would be dead code.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemanticVersion> {

    /** Classic component-by-component ordering; "1.2" == "1.2.0" == "v1.2.0". */
    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {

        /**
         * Parses a release tag or version name such as "v0.0.72", "0.0.72",
         * "1.2" or "2.0.0-beta.1".
         *
         * - an optional leading "v"/"V" is ignored,
         * - anything after the first "-" or "+" is ignored (pre-release and
         *   build metadata do not take part in the comparison),
         * - missing trailing components count as zero, so "1.2" == "1.2.0",
         * - more than three components, an empty component ("1..2") or a
         *   non-numeric component returns null. Callers treat null as
         *   "cannot compare, so do not offer an update" instead of crashing
         *   or guessing — a malformed remote tag must never break the app.
         */
        fun parse(raw: String?): SemanticVersion? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            val withoutPrefix =
                if (trimmed.startsWith("v") || trimmed.startsWith("V")) trimmed.substring(1)
                else trimmed
            val core = withoutPrefix.substringBefore('-').substringBefore('+')
            if (core.isBlank()) return null
            val parts = core.split('.')
            if (parts.size > 3) return null
            val numbers = intArrayOf(0, 0, 0)
            for (i in parts.indices) {
                if (parts[i].isBlank()) return null
                val value = parts[i].toIntOrNull() ?: return null
                if (value < 0) return null
                numbers[i] = value
            }
            return SemanticVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
