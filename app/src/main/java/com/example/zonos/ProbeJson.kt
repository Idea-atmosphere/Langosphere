package com.example.zonos

/**
 * Unwrap a WebView.evaluateJavascript result value (v0.2.5).
 *
 * The result callback receives the JS value JSON-encoded: a JS string
 * arrives wrapped in double quotes with its contents escaped, e.g. the
 * probe's JSON.stringify({...}) comes back as
 *   "\"{\\\"generate\\\":\\\"function\\\",\\\"gpu\\\":true}\""
 * Feeding that straight to JSONObject() throws (it is a JSON string, not
 * an object) — which is why a probe can silently stay unanswered: the
 * parse threw and the catch swallowed it, in both the reader probe and
 * the Studio mount probe. Unquote first, then parse.
 *
 * Pure Kotlin, no Android deps, so the JVM harness in
 * build/tests/probe/ can run it without a device.
 */
fun unwrapJsStringResult(json: String?): String? {
    if (json == null) return null
    val t = json.trim()
    if (t.length < 2 || !t.startsWith("\"") || !t.endsWith("\"")) return t
    return unescapeJsonString(t.substring(1, t.length - 1))
}

/** Minimal JSON string unescape (\" \\ \/ \b \f \n \r \t \uXXXX). */
private fun unescapeJsonString(s: String): String {
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c != '\\' || i + 1 >= s.length) {
            out.append(c)
            i++
            continue
        }
        when (val e = s[i + 1]) {
            '"' -> {
                out.append('"')
                i += 2
            }
            '\\' -> {
                out.append('\\')
                i += 2
            }
            '/' -> {
                out.append('/')
                i += 2
            }
            'b' -> {
                out.append('\b')
                i += 2
            }
            'f' -> {
                out.append('\u000C')
                i += 2
            }
            'n' -> {
                out.append('\n')
                i += 2
            }
            'r' -> {
                out.append('\r')
                i += 2
            }
            't' -> {
                out.append('\t')
                i += 2
            }
            'u' -> {
                if (i + 5 < s.length) {
                    val code = s.substring(i + 2, i + 6).toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar())
                        i += 6
                        continue
                    }
                }
                out.append(e)
                i += 2
            }
            else -> {
                out.append(e)
                i += 2
            }
        }
    }
    return out.toString()
}
