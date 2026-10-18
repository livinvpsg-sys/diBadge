package com.fabxdi.dibadge.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Automatic numbered and lettered lists for the "Add note" field.
 *
 * - Marker: optional spaces, 1 to 3 digits OR one letter (a-z, A-Z), a dot, then a space ("1. ", "b. ").
 * - Enter on a marker line continues the list with the next marker (1. -> 2., a. -> b., A. -> B.).
 * - Enter on an empty item (marker only) removes the marker and ends the list.
 * - Enter in the middle of a line moves the text after the cursor to a new marker line,
 *   and the following items of the same list are renumbered.
 * - Backspace right after "N." (after deleting the space) removes the whole marker.
 * - Lists stop at 999 and after z / Z (the next Enter is a plain newline).
 * - Text is plain; nothing is stored except the typed characters.
 * - Paste and any change that is not a single typed Enter or a single Backspace is left untouched.
 */
object NumberedListEditor {

    // Group 1: leading spaces/tabs. Group 2: the marker (1-3 digits or one letter).
    private val MARKER_START_REGEX = Regex("^([ \\t]*)(\\d{1,3}|[a-zA-Z])\\. ")

    // A line that is only a marker without its trailing space, e.g. "  3." or "b."
    private val MARKER_NO_SPACE_REGEX = Regex("^[ \\t]*(\\d{1,3}|[a-zA-Z])\\.$")

    /**
     * Call from the text field's onValueChange. Returns the value to store in state.
     * [maxLength] is the field's maximum length; the caller should also reject changes that exceed it.
     */
    fun onValueChange(oldValue: TextFieldValue, newValue: TextFieldValue, maxLength: Int): TextFieldValue {
        val lengthDiff = newValue.text.length - oldValue.text.length

        // 1. Backspace right after a marker: "3. |" -> after deleting the space we have "3.|" -> remove "3."
        if (lengthDiff == -1 &&
            oldValue.selection.collapsed &&
            newValue.selection.collapsed &&
            newValue.selection.start == oldValue.selection.start - 1
        ) {
            val deletedChar = oldValue.text[oldValue.selection.start - 1]
            if (deletedChar == ' ') {
                val beforeCursor = newValue.text.substring(0, newValue.selection.start)
                val lineStart = beforeCursor.lastIndexOf('\n') + 1
                val lineBeforeCursor = beforeCursor.substring(lineStart)
                if (MARKER_NO_SPACE_REGEX.matches(lineBeforeCursor)) {
                    val newText = newValue.text.removeRange(lineStart, newValue.selection.start)
                    return TextFieldValue(text = newText, selection = TextRange(lineStart))
                }
            }
            return newValue
        }

        // 2. Enter replacing a selection
        val selectionReplacedByEnter = !oldValue.selection.collapsed &&
                newValue.selection.collapsed &&
                newValue.text == oldValue.text.replaceRange(oldValue.selection.min, oldValue.selection.max, "\n") &&
                newValue.selection.start == oldValue.selection.min + 1

        // 3. A single typed Enter
        val singleEnter = lengthDiff == 1 &&
                oldValue.selection.collapsed &&
                newValue.selection.collapsed &&
                newValue.selection.start == oldValue.selection.start + 1 &&
                newValue.text[newValue.selection.start - 1] == '\n'

        return if (singleEnter || selectionReplacedByEnter) {
            handleEnter(newValue, maxLength)
        } else {
            newValue
        }
    }

    /** [value] already contains the typed '\n' just before the cursor. */
    private fun handleEnter(value: TextFieldValue, maxLength: Int): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start
        val before = text.substring(0, cursor - 1)   // text without the typed '\n'
        val after = text.substring(cursor)
        val lineStart = before.lastIndexOf('\n') + 1
        val lineBefore = before.substring(lineStart)

        val match = MARKER_START_REGEX.find(lineBefore) ?: return value
        val indent = match.groupValues[1]
        val marker = match.groupValues[2]
        val restOfLine = after.substringBefore('\n')

        // Empty item (only the marker on the line): remove the marker and end the list.
        if (lineBefore == match.value && restOfLine.isEmpty()) {
            return TextFieldValue(
                text = text.substring(0, lineStart) + after,
                selection = TextRange(lineStart)
            )
        }

        val next = getNextMarker(marker) ?: return value          // 999 or z/Z: plain newline
        val newMarker = "$indent$next. "
        if (text.length + newMarker.length > maxLength) return value

        val newText = before + "\n" + newMarker + after
        val newCursor = before.length + 1 + newMarker.length
        val renumbered = renumberFollowingItems(newText, newCursor, indent, next)
        if (renumbered.length > maxLength) return value            // e.g. 9 -> 10 would exceed the limit
        return TextFieldValue(text = renumbered, selection = TextRange(newCursor))
    }

    private fun getNextMarker(marker: String): String? {
        val num = marker.toIntOrNull()
        if (num != null) {
            return if (num >= 999) null else (num + 1).toString()
        }
        if (marker.length == 1) {
            val c = marker[0]
            if (c == 'z' || c == 'Z') return null
            return (c + 1).toString()
        }
        return null
    }

    /**
     * Renumbers the consecutive items that follow the line containing [cursor], so they continue after [startMarker].
     * Stops at the first line that is not a marker line, has a different indentation,
     * is of a different kind (number vs letter) or a different letter case.
     * Only text after the cursor changes, so the cursor position does not move.
     */
    private fun renumberFollowingItems(
        text: String,
        cursor: Int,
        targetIndentation: String,
        startMarker: String
    ): String {
        val lines = text.split('\n').toMutableList()

        // Find the line that contains the cursor
        var offset = 0
        var startLineIndex = -1
        for (i in lines.indices) {
            offset += lines[i].length + 1
            if (offset > cursor) {
                startLineIndex = i
                break
            }
        }
        if (startLineIndex == -1 || startLineIndex == lines.lastIndex) return text

        val isNumber = startMarker.toIntOrNull() != null
        val isUpperCase = !isNumber && startMarker[0].isUpperCase()
        var currentMarker = startMarker

        for (i in startLineIndex + 1 until lines.size) {
            val line = lines[i]
            val match = MARKER_START_REGEX.find(line) ?: break           // not a marker line
            if (match.groupValues[1] != targetIndentation) break          // indentation changed

            val markerVal = match.groupValues[2]
            val lineIsNumber = markerVal.toIntOrNull() != null
            if (lineIsNumber != isNumber) break                           // number vs letter
            if (!isNumber && markerVal[0].isUpperCase() != isUpperCase) break // upper vs lower case

            val nextMarker = getNextMarker(currentMarker) ?: break
            lines[i] = "$targetIndentation$nextMarker. " + line.substring(match.value.length)
            currentMarker = nextMarker
        }
        return lines.joinToString("\n")
    }
}
