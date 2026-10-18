package com.fabxdi.dibadge.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberedListEditorTest {

    private fun change(oldText: String, oldCursor: Int, newText: String, newCursor: Int): TextFieldValue {
        val old = TextFieldValue(oldText, TextRange(oldCursor))
        val new = TextFieldValue(newText, TextRange(newCursor))
        return NumberedListEditor.onValueChange(old, new, 2000)
    }

    @Test
    fun testEnterAtEndOfNumberedLine() {
        val result = change("1. Buy cable", 12, "1. Buy cable\n", 13)
        assertEquals("1. Buy cable\n2. ", result.text)
        assertEquals(16, result.selection.start)
    }

    @Test
    fun testEnterInMiddleOfNumberedLine() {
        val result = change("1. Buy cable", 7, "1. Buy\n cable", 8)
        assertEquals("1. Buy\n2.  cable", result.text)
        assertEquals(11, result.selection.start)
    }

    @Test
    fun testEnterOnEmptyNumberedLineEndsList() {
        // Pressing Enter when line is just "4. "
        val result = change("1. a\n2. b\n3. c\n4. ", 18, "1. a\n2. b\n3. c\n4. \n", 19)
        assertEquals("1. a\n2. b\n3. c\n", result.text)
        assertEquals(15, result.selection.start)
    }

    @Test
    fun testEnterOnPlainLine() {
        val result = change("Hello", 5, "Hello\n", 6)
        assertEquals("Hello\n", result.text)
    }

    @Test
    fun testInsertInMiddleWithRenumbering() {
        val result = change("1. a\n2. c", 4, "1. a\n\n2. c", 5)
        assertEquals("1. a\n2. \n3. c", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun testMaxDigitsLimit() {
        // Entering on 999 should not produce 1000
        val result = change("999. Last", 9, "999. Last\n", 10)
        assertEquals("999. Last\n", result.text)
    }

    @Test
    fun testLetteredLists() {
        val result = change("a. Apple", 8, "a. Apple\n", 9)
        assertEquals("a. Apple\nb. ", result.text)

        val resultUpper = change("Z. Zebra", 8, "Z. Zebra\n", 9)
        // Z should not wrap or continue
        assertEquals("Z. Zebra\n", resultUpper.text)
    }

    @Test
    fun testBackspaceRemovesMarker() {
        val result = change("1. a\n2. ", 8, "1. a\n2.", 7)
        assertEquals("1. a\n", result.text)
        assertEquals(5, result.selection.start)
    }

    @Test
    fun testIndentationIsKept() {
        val result = change("  1. Indented", 13, "  1. Indented\n", 14)
        assertEquals("  1. Indented\n  2. ", result.text)
    }
}
