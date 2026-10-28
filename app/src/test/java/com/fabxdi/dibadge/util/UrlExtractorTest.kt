package com.fabxdi.dibadge.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun extractUrls_emptyText_returnsEmptyList() {
        val result = UrlExtractor.extractUrls("")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun extractUrls_singleUrl_extractsCorrectly() {
        val text = "Check out https://portal.harbourworks.sg/doc/123 for details."
        val result = UrlExtractor.extractUrls(text)
        assertEquals(listOf("https://portal.harbourworks.sg/doc/123"), result)
    }

    @Test
    fun extractUrls_multipleUrls_extractsAll() {
        val text = "Visit www.example.com and http://test.org/path! Also check https://site.co.uk."
        val result = UrlExtractor.extractUrls(text)
        assertEquals(listOf("www.example.com", "http://test.org/path", "https://site.co.uk"), result)
    }

    @Test
    fun extractUrls_withPunctuation_trimsTrailingPunctuation() {
        val text = "Link: https://portal.harbourworks.sg/doc/123, and www.google.com; ok?"
        val result = UrlExtractor.extractUrls(text)
        assertEquals(listOf("https://portal.harbourworks.sg/doc/123", "www.google.com"), result)
    }

    @Test
    fun extractDomain_validUrls_extractsCleanDomain() {
        assertEquals("portal.harbourworks.sg", UrlExtractor.extractDomain("https://portal.harbourworks.sg/doc/123"))
        assertEquals("example.com", UrlExtractor.extractDomain("www.example.com"))
        assertEquals("test.org", UrlExtractor.extractDomain("http://test.org/path"))
    }
}
