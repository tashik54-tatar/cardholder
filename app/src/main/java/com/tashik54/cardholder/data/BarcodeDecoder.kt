package com.tashik54.cardholder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Расшифровка штрихкода/QR из изображения (кнопка «Из файла»). */
object BarcodeDecoder {

    private const val MAX_DIM = 2048

    suspend fun decode(context: Context, uri: Uri): Barcode? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(context, uri) ?: return@withContext null
        decode(context, bitmap)
    }

    /** Расшифровка кода с готового снимка (фото стороны карты). */
    suspend fun decode(context: Context, bitmap: Bitmap, rotationDegrees: Int = 0): Barcode? =
        withContext(Dispatchers.IO) {
            val scanner = BarcodeScanning.getClient(allFormatsOptions())
            try {
                scanWith(scanner, bitmap, rotationDegrees)
            } finally {
                scanner.close()
            }
        }

    /**
     * Настойчивая расшифровка: если код не найден на снимке как есть,
     * пробует повороты на 90/180/270 (EXIF мог не сохраниться).
     * Один сканер ML Kit переиспользуется для всех попыток.
     */
    suspend fun decodeBestEffort(context: Context, bitmap: Bitmap): Barcode? =
        withContext(Dispatchers.IO) {
            val scanner = BarcodeScanning.getClient(allFormatsOptions())
            try {
                scanWith(scanner, bitmap, 0)?.let { return@withContext it }
                val matrix = android.graphics.Matrix()
                for (angle in listOf(90f, 180f, 270f)) {
                    matrix.postRotate(90f)
                    val rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
                    )
                    scanWith(scanner, rotated, 0)?.let { return@withContext it }
                }
                null
            } finally {
                scanner.close()
            }
        }

    private fun scanWith(
        scanner: BarcodeScanner,
        bitmap: Bitmap,
        rotationDegrees: Int,
    ): Barcode? = try {
        Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, rotationDegrees))).firstOrNull()
    } catch (_: Exception) {
        null
    }

    /**
     * Загрузка картинки по Uri с даунсэмплом до MAX_DIM по большей стороне
     * (на любом API) — иначе фото 50+ Мп декодируется в память целиком (OOM).
     */
    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > MAX_DIM) {
                    decoder.setTargetSampleSize(maxSide / MAX_DIM + 1)
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_DIM || bounds.outHeight / (sample * 2) >= MAX_DIM) {
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
