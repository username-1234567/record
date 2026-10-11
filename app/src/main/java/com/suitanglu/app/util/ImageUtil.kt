package com.suitanglu.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtil {
    private const val BUF = 1 shl 16

    /** 按设置压缩图片；关闭压缩时返回原图字节 */
    fun loadForUpload(file: File, compress: Boolean, quality: Int): Pair<ByteArray, String> {
        if (!compress) return file.readBytes() to mimeOf(file.name)
        val bytes = compressFile(file, quality)
        return bytes to "image/jpeg"
    }

    fun compressFile(file: File, quality: Int): ByteArray {
        val bmp = decodeOriented(file) ?: return file.readBytes()
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), out)
        bmp.recycle()
        return out.toByteArray()
    }

    fun decodeOriented(file: File): Bitmap? {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return bmp
        val rotation = when (
            runCatching { exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bmp
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }

    fun mimeOf(name: String): String = when (File(name).extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }
}
