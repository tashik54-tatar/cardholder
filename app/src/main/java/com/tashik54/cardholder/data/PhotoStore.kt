package com.tashik54.cardholder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

/**
 * Хранение фото сторон карты: файлы в приватном каталоге приложения
 * (files/photos), в БД — только пути.
 */
object PhotoStore {

    private const val PHOTOS_DIR = "photos"
    private const val CAPTURES_DIR = "captures"

    /** Файл для снимка камеры (TakePicture требует заранее созданный Uri). */
    fun newCaptureFile(context: Context): File =
        File(context.filesDir, CAPTURES_DIR).apply { mkdirs() }
            .resolve("capture_${UUID.randomUUID()}.jpg")

    /** Сохраняет обрезанное фото в постоянное хранилище, возвращает путь. */
    fun save(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val file = dir.resolve("photo_${UUID.randomUUID()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    /**
     * Удаляет файл фото (при замене стороны). Путь обязан указывать
     * внутрь files/photos — пути приходят из БД, поэтому чужие/подменённые
     * пути молча игнорируются (защита в глубину от path traversal).
     */
    fun delete(context: Context, path: String?) {
        if (path == null) return
        runCatching {
            val photosDir = File(context.filesDir, PHOTOS_DIR).canonicalFile
            val target = File(path).canonicalFile
            if (target.parentFile == photosDir) target.delete()
        }
    }

    /**
     * Чистит временный каталог captures/: снимки камеры удаляются
     * сразу после обработки, но при смерти процесса между снимком и
     * обработкой файлы могли остаться — убираем их при старте приложения.
     */
    fun cleanupCaptures(context: Context) {
        runCatching {
            File(context.filesDir, CAPTURES_DIR).listFiles()?.forEach { it.delete() }
        }
    }

    /** Загрузка с даунсэмплом до maxDim по большей стороне. */
    fun load(path: String?, maxDim: Int = 1024): Bitmap? {
        if (path == null) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        }
    }

    /** Загрузка картинки из галереи по Uri с даунсэмплом (для фото стороны). */
    fun loadFromUri(context: Context, uri: android.net.Uri, maxDim: Int = 2048): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Загрузка снимка камеры с учётом поворота из EXIF — иначе фото
     * с некоторых устройств сохраняется «лёжа на боку». TRANSPOSE/TRANSVERSE
     * (поворот + отражение по горизонтали) тоже учитываются.
     */
    fun loadCapture(file: File, maxDim: Int = 2048): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val degrees: Float
        val flipX: Boolean
        when (
            try {
                androidx.exifinterface.media.ExifInterface(file.absolutePath).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )
            } catch (_: Exception) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            }
        ) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                degrees = 90f; flipX = false
            }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                degrees = 180f; flipX = false
            }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                degrees = 270f; flipX = false
            }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                degrees = 90f; flipX = true
            }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                degrees = 270f; flipX = true
            }
            else -> {
                degrees = 0f; flipX = false
            }
        }
        if (degrees == 0f && !flipX) return bitmap
        val matrix = android.graphics.Matrix().apply {
            postRotate(degrees)
            if (flipX) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
