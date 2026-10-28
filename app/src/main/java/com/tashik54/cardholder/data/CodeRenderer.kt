package com.tashik54.cardholder.data

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.toColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap

/**
 * Отрисовка кода картинкой для предъявления на кассе:
 * QR — из значения; штрихкоды — в исходном формате, а если значение
 * не подходит под формат (например, ручной ввод без контрольной цифры
 * для EAN-13) — fallback в CODE_128, который принимает любую строку.
 */
object CodeRenderer {

    fun render(
        value: String,
        codeType: CodeType,
        barcodeFormat: String?,
        width: Int = 1080,
        height: Int = 360,
    ): Bitmap {
        val matrix = when (codeType) {
            CodeType.QR -> QRCodeWriter().encode(
                value,
                BarcodeFormat.QR_CODE,
                width,
                width,
            )
            CodeType.BARCODE -> encode1D(value, barcodeFormat, width, height)
                ?: encode1D(value, "CODE_128", width, height)
                ?: MultiFormatWriter().encode(
                    value,
                    BarcodeFormat.CODE_128,
                    width,
                    height,
                )
        }
        return matrix.toBitmap()
    }

    private fun encode1D(value: String, formatName: String?, width: Int, height: Int): BitMatrix? {
        val format = when (formatName) {
            "CODE_128" -> BarcodeFormat.CODE_128
            "CODE_39" -> BarcodeFormat.CODE_39
            "CODE_93" -> BarcodeFormat.CODE_93
            "EAN_13" -> BarcodeFormat.EAN_13
            "EAN_8" -> BarcodeFormat.EAN_8
            "UPC_A" -> BarcodeFormat.UPC_A
            "UPC_E" -> BarcodeFormat.UPC_E
            "CODABAR" -> BarcodeFormat.CODABAR
            "ITF" -> BarcodeFormat.ITF
            else -> return null
        }
        return try {
            MultiFormatWriter().encode(value, format, width, height)
        } catch (_: Exception) {
            null
        }
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val bitmap = createBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap[x, y] = if (get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return bitmap
    }
}
