package com.fabxdi.dibadge.util

import java.net.URI

object UrlExtractor {
    fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val regex = Regex("""(https?://[^\s,;!\)]+|www\.[^\s,;!\)]+)""", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { match ->
            match.value.trimEnd('.', ',', ';', '!', ')', ']', '}')
        }.filter { it.isNotBlank() }.toList()
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
