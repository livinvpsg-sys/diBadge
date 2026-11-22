package com.fabxdi.dibadge.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Makes a real, scannable QR code (ZXing). Black modules on white, 2-module quiet zone.
 * High error correction, so a dot or logo drawn over the middle does not stop it scanning.
 */
object QrCodeGenerator {

    fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap {
        // An empty text cannot be encoded; give a blank tile instead of crashing
        if (payload.isBlank()) {
            return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }
}