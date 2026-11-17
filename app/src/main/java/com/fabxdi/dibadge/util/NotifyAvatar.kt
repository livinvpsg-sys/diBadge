package com.fabxdi.dibadge.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.LruCache
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Round pictures for notifications: the person's photo when there is one, otherwise a coloured
 * circle with their first letter. Call from a background thread (a push is handled on one).
 */
object NotifyAvatar {
    private const val SIZE = 128
    private val palette = intArrayOf(
        0xFFE39A6B.toInt(), 0xFF6BA8E3.toInt(), 0xFF9B7BD6.toInt(),
        0xFF5FB88C.toInt(), 0xFFD66B8A.toInt(), 0xFFC9A93F.toInt()
    )
    private val cache = LruCache<String, Bitmap>(24)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /** The round picture for [name]: the photo at [photoUrl], or a coloured initial if it can't be loaded. */
    fun bitmap(name: String, photoUrl: String): Bitmap {
        val key = photoUrl.ifBlank { "initial:$name" }
        cache.get(key)?.let { return it }
        val made = (if (photoUrl.isNotBlank()) fromUrl(photoUrl) else null) ?: initial(name)
        cache.put(key, made)
        return made
    }

    fun person(name: String, photoUrl: String): Person =
        Person.Builder()
            .setName(name)
            .setIcon(IconCompat.createWithBitmap(bitmap(name, photoUrl)))
            .build()

    private fun fromUrl(url: String): Bitmap? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body?.bytes() ?: return null
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            circle(raw)
        }
    } catch (e: Exception) {
        null
    }

    private fun circle(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(square, Rect(0, 0, side, side), Rect(0, 0, SIZE, SIZE), p)
        return out
    }

    private fun initial(name: String): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = palette[(name.hashCode() and 0x7FFFFFFF) % palette.size]
        c.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = SIZE * 0.5f
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        c.drawText(letter, SIZE / 2f, SIZE / 2f - (p.descent() + p.ascent()) / 2f, p)
        return out
    }
}