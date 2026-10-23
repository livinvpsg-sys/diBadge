package com.fabxdi.dibadge.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object QrCodeGenerator {

    fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val matrixSize = 25
        val cellSize = sizePx.toFloat() / matrixSize
        val margin = 2

        val matrix = Array(matrixSize) { BooleanArray(matrixSize) }

        // Finder Pattern 1: Top-Left
        drawFinderPattern(matrix, 0, 0)
        // Finder Pattern 2: Top-Right
        drawFinderPattern(matrix, matrixSize - 7, 0)
        // Finder Pattern 3: Bottom-Left
        drawFinderPattern(matrix, 0, matrixSize - 7)

        // Timing Patterns
        for (i in 8 until matrixSize - 8) {
            matrix[6][i] = (i % 2 == 0)
            matrix[i][6] = (i % 2 == 0)
        }

        // Encode payload bits deterministically into matrix
        val hash = payload.hashCode()
        var bitIndex = 0
        val bytes = payload.toByteArray()

        for (row in 8 until matrixSize - 1) {
            for (col in 8 until matrixSize - 1) {
                if (row == 6 || col == 6) continue
                val byteVal = if (bitIndex / 8 < bytes.size) bytes[bitIndex / 8].toInt() else hash
                val bit = ((byteVal shr (bitIndex % 8)) and 1) == 1
                matrix[row][col] = bit
                bitIndex++
            }
        }

        // Draw Matrix to Canvas
        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                if (matrix[r][c]) {
                    val left = c * cellSize
                    val top = r * cellSize
                    val right = left + cellSize
                    val bottom = top + cellSize
                    canvas.drawRect(left, top, right, bottom, paint)
                }
            }
        }

        return bitmap
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startR: Int, startC: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isBorder = (r == 0 || r == 6 || c == 0 || c == 6)
                val isCenter = (r in 2..4 && c in 2..4)
                if (isBorder || isCenter) {
                    matrix[startR + r][startC + c] = true
                }
            }
        }
    }
}
