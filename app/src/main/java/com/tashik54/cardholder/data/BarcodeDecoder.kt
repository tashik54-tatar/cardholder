package com.tashik54.cardholder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Расшифровка штрихкода/QR из изображения (кнопка «Из файла»). */
object BarcodeDecoder {

    suspend fun decode(context: Context, uri: Uri): Barcode? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(context, uri) ?: return@withContext null
        decode(context, bitmap)
    }

    /** Расшифровка кода с готового снимка (фото стороны карты). */
    suspend fun decode(context: Context, bitmap: Bitmap, rotationDegrees: Int = 0): Barcode? =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            val scanner = BarcodeScanning.getClient(allFormatsOptions())
            try {
                Tasks.await(scanner.process(image)).firstOrNull()
            } catch (_: Exception) {
                null
            } finally {
                scanner.close()
            }
        }

    /**
     * Настойчивая расшифровка: если код не найден на снимке как есть,
     * пробует повороты на 90/180/270 (EXIF мог не сохраниться).
     */
    suspend fun decodeBestEffort(context: Context, bitmap: Bitmap): Barcode? {
        decode(context, bitmap)?.let { return it }
        val matrix = android.graphics.Matrix()
        for (angle in listOf(90f, 180f, 270f)) {
            matrix.postRotate(90f)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            decode(context, rotated)?.let { return it }
        }
        return null
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 2048 || bounds.outHeight / (sample * 2) >= 2048) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
    } catch (_: Exception) {
        null
    }
}
