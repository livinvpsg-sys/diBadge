package com.fabxdi.dibadge.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PhotoCompressionResult(
    val fullUri: Uri,
    val thumbUri: Uri,
    val width: Int,
    val height: Int,
    val fileSize: Long
)

data class VideoCompressionResult(
    val fullUri: Uri,
    val thumbUri: Uri,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fileSize: Long
)

object MediaCompressor {

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "compression_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cleanCache(context: Context) {
        try {
            val dir = getCacheDir(context)
            dir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readImageSize(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun compressPhoto(context: Context, uri: Uri): PhotoCompressionResult = withContext(Dispatchers.IO) {
        val fileName = FilePickerUtils.getFileName(context, uri).lowercase()
        val isGif = fileName.endsWith(".gif") || context.contentResolver.getType(uri) == "image/gif"

        if (isGif) {
            val size = FilePickerUtils.getFileSizeBytes(context, uri)
            val (gifW, gifH) = readImageSize(context, uri) ?: (480 to 480)
            return@withContext PhotoCompressionResult(
                fullUri = uri,
                thumbUri = uri,
                width = gifW,
                height = gifH,
                fileSize = size
            )
        }

        try {
            // 1. Read EXIF Orientation
            var rotationDegrees = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    rotationDegrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Sub-sample decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val origW = options.outWidth
            val origH = options.outHeight

            var sampleSize = 1
            val maxDim = maxOf(origW, origH)
            while (maxDim / sampleSize > 3200) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: throw IllegalStateException("Could not decode bitmap")

            // 3. Apply rotation
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                    decodedBitmap = rotated
                }
            }

            val curW = decodedBitmap.width
            val curH = decodedBitmap.height
            val curLong = maxOf(curW, curH)

            val scale = if (curLong > 1600) 1600f / curLong else 1.0f
            val targetW = (curW * scale).toInt().coerceAtLeast(1)
            val targetH = (curH * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(decodedBitmap, targetW, targetH, true)
            } else {
                decodedBitmap
            }

            // Fill transparent background with solid white
            val finalPhotoBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalPhotoBitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)

            val cacheFolder = getCacheDir(context)
            val timestamp = System.currentTimeMillis()
            val fullFile = File(cacheFolder, "photo_compressed_${timestamp}.jpg")

            FileOutputStream(fullFile).use { out ->
                finalPhotoBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            // 4. Generate 480px Tile Thumbnail
            val thumbLong = maxOf(targetW, targetH)
            val thumbScale = if (thumbLong > 480) 480f / thumbLong else 1.0f
            val thumbW = (targetW * thumbScale).toInt().coerceAtLeast(1)
            val thumbH = (targetH * thumbScale).toInt().coerceAtLeast(1)

            val thumbBitmap = Bitmap.createScaledBitmap(finalPhotoBitmap, thumbW, thumbH, true)
            val thumbFile = File(cacheFolder, "photo_thumb_${timestamp}.jpg")

            FileOutputStream(thumbFile).use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }

            // Free the large bitmaps now instead of waiting for the garbage collector
            thumbBitmap.recycle()
            finalPhotoBitmap.recycle()
            if (scaledBitmap !== decodedBitmap) scaledBitmap.recycle()
            decodedBitmap.recycle()

            val fullUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fullFile)
            val thumbUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", thumbFile)

            PhotoCompressionResult(
                fullUri = fullUri,
                thumbUri = thumbUri,
                width = targetW,
                height = targetH,
                fileSize = fullFile.length()
            )
        } catch (e: Throwable) {
            // Includes OutOfMemoryError on very large photos
            e.printStackTrace()
            // Fallback to original
            val size = FilePickerUtils.getFileSizeBytes(context, uri)
            val (origW, origH) = readImageSize(context, uri) ?: (1600 to 1200)
            PhotoCompressionResult(
                fullUri = uri,
                thumbUri = uri,
                width = origW,
                height = origH,
                fileSize = size
            )
        }
    }

    suspend fun compressVideo(context: Context, uri: Uri): VideoCompressionResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val origDuration = durationStr?.toLongOrNull() ?: 0L
            var origW = widthStr?.toIntOrNull() ?: 1280
            var origH = heightStr?.toIntOrNull() ?: 720
            val rotation = rotationStr?.toIntOrNull() ?: 0

            if (rotation == 90 || rotation == 270) {
                val tmp = origW
                origW = origH
                origH = tmp
            }

            // Generate Video Frame Thumbnail at 1 second
            val cacheFolder = getCacheDir(context)
            val timestamp = System.currentTimeMillis()
            val frameBitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)

            val thumbFile = File(cacheFolder, "vid_thumb_${timestamp}.jpg")
            if (frameBitmap != null) {
                val maxDim = maxOf(frameBitmap.width, frameBitmap.height)
                val scale = if (maxDim > 480) 480f / maxDim else 1.0f
                val tw = (frameBitmap.width * scale).toInt().coerceAtLeast(1)
                val th = (frameBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaledFrame = Bitmap.createScaledBitmap(frameBitmap, tw, th, true)

                FileOutputStream(thumbFile).use { out ->
                    scaledFrame.compress(Bitmap.CompressFormat.JPEG, 70, out)
                }
            } else {
                thumbFile.createNewFile()
            }

            val thumbUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", thumbFile)
            val origSize = FilePickerUtils.getFileSizeBytes(context, uri)

            VideoCompressionResult(
                fullUri = uri,
                thumbUri = thumbUri,
                width = origW,
                height = origH,
                durationMs = origDuration,
                fileSize = origSize
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val origSize = FilePickerUtils.getFileSizeBytes(context, uri)
            val thumbFile = File(getCacheDir(context), "vid_thumb_${System.currentTimeMillis()}.jpg")
            thumbFile.createNewFile()
            val thumbUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", thumbFile)

            VideoCompressionResult(
                fullUri = uri,
                thumbUri = thumbUri,
                width = 1280,
                height = 720,
                durationMs = 0L,
                fileSize = origSize
            )
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }
}
