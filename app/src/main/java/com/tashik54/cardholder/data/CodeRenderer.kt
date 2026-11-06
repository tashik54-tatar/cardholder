package com.tashik54.cardholder.data

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Отрисовка кода картинкой для предъявления на кассе: QR — из значения;
 * штрихкоды — в исходном формате, а если значение не подходит под формат
 * (например, ручной ввод без контрольной цифры для EAN-13) — fallback
 * в CODE_128, который принимает любую строку.
 *
 * PDF417/AZTEC/DATA_MATRIX — двумерные форматы, рендерятся своими
 * писателями ZXing (иначе раньше молча превращались в CODE_128,
 * который касса не могла прочитать).
 *
 * Метод возвращает null, если значение невозможно закодировать ни одним
 * способом (пустая строка, не-латиница для CODE_128, длина > 80) —
 * вызывающая сторона обязана обработать null вместо падения.
 */
object CodeRenderer {

    fun render(
        value: String,
        codeType: CodeType,
        barcodeFormat: String?,
        width: Int = 1080,
        height: Int = 360,
    ): Bitmap? {
        val matrix = when (codeType) {
            CodeType.QR -> tryEncode(width, width) { w, h ->
                QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, w, h)
            }
            CodeType.BARCODE -> encode1D(value, barcodeFormat, width, height)
                ?: encode2D(value, barcodeFormat, width)
                ?: encode1D(value, "CODE_128", width, height)
        }
        return matrix?.toBitmap()
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
        return tryEncode(width, height) { w, h -> MultiFormatWriter().encode(value, format, w, h) }
    }

    /** Двумерные форматы, хранящиеся с типом BARCODE. */
    private fun encode2D(value: String, formatName: String?, size: Int): BitMatrix? {
        val (format, height) = when (formatName) {
            "PDF417" -> BarcodeFormat.PDF_417 to size / 3
            "AZTEC" -> BarcodeFormat.AZTEC to size
            "DATA_MATRIX" -> BarcodeFormat.DATA_MATRIX to size
            else -> return null
        }
        return tryEncode(size, height) { w, h -> MultiFormatWriter().encode(value, format, w, h) }
    }

    /** Единая точка обработки исключений ZXing: любой провал — null, не крах. */
    private inline fun tryEncode(width: Int, height: Int, block: (Int, Int) -> BitMatrix): BitMatrix? =
        try {
            block(width, height)
        } catch (_: Exception) {
            null
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
