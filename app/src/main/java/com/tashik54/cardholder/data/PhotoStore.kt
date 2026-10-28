package com.tashik54.cardholder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Хранение фото сторон карты: файлы в приватном каталоге приложения
 * (files/photos), в БД — только пути.
 */
object PhotoStore {

    /** Файл для снимка камеры (TakePicture требует заранее созданный Uri). */
    fun newCaptureFile(context: Context): File =
        File(context.filesDir, "captures").apply { mkdirs() }
            .resolve("capture_${UUID.randomUUID()}.jpg")

    /** Сохраняет обрезанное фото в постоянное хранилище, возвращает путь. */
    fun save(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = dir.resolve("photo_${UUID.randomUUID()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    /** Удаляет файл фото (при замене стороны). */
    fun delete(path: String?) {
        if (path != null) File(path).delete()
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

    /** Декодирование снимка камеры по Uri (FileProvider). */
    fun loadFromUri(context: Context, uri: Uri): Bitmap? = load(uri.path, maxDim = 2048)
}
