package com.fabxdi.dibadge.util

import java.net.URI

object UrlExtractor {
    private val URL_REGEX = Regex("""(https?://[^\s<>"']+|www\.[^\s<>"']+)""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?'\"]}>"

    fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return URL_REGEX.findAll(text).map { trimUrl(it.value) }.filter { it.isNotBlank() }.toList()
    }

    private fun trimUrl(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            val remove = when {
                last in TRAILING -> true
                // keep ")" only when the URL opened a "(" (e.g. Wikipedia links)
                last == ')' -> url.count { it == ')' } > url.count { it == '(' }
                else -> false
            }
            if (!remove) break
            url = url.dropLast(1)
        }
        return url
    }

    fun extractDomain(urlStr: String): String {
        if (urlStr.isBlank()) return ""
        return try {
            val cleanUrl = if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) "https://$urlStr" else urlStr
            val uri = URI(cleanUrl)
            val host = uri.host ?: cleanUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
            host.removePrefix("www.")
        } catch (e: Exception) {
            urlStr.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")
        }
    }
}
