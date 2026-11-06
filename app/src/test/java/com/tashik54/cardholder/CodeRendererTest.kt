package com.tashik54.cardholder

import com.tashik54.cardholder.data.CodeRenderer
import com.tashik54.cardholder.data.CodeType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Регресс критического бага аудита: рендер кода не должен падать
 * ни на каком значении — максимум вернуть null (раньше кириллица —
 * символ вне ISO-8859-1 для CODE_128 — роняла экран карты).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodeRendererTest {

    @Test
    fun `кириллица в штрихкоде не падает а возвращает null`() {
        val bitmap = CodeRenderer.render("ПриветМир", CodeType.BARCODE, null)
        assertNull(bitmap)
    }

    @Test
    fun `длинная ascii строка кодируется без падения`() {
        val long = "A".repeat(120)
        assertNotNull(CodeRenderer.render(long, CodeType.BARCODE, null))
    }

    @Test
    fun `длинная строка с кириллицей возвращает null`() {
        assertNull(CodeRenderer.render("П".repeat(120), CodeType.BARCODE, null))
    }

    @Test
    fun `пустое значение возвращает null`() {
        assertNull(CodeRenderer.render("", CodeType.BARCODE, null))
        assertNull(CodeRenderer.render("", CodeType.QR, null))
    }

    @Test
    fun `латиница кодируется кодом 128`() {
        val bitmap = CodeRenderer.render("ABC-123456", CodeType.BARCODE, null)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
    }

    @Test
    fun `ean13 с неверной длиной уходит в fallback CODE_128`() {
        // Значение не подходит под EAN-13 — должен сработать fallback
        val bitmap = CodeRenderer.render("ABC123", CodeType.BARCODE, "EAN_13")
        assertNotNull(bitmap)
    }

    @Test
    fun `qr рендерится и с кириллицей`() {
        val bitmap = CodeRenderer.render("ПриветМир", CodeType.QR, null)
        assertNotNull(bitmap)
    }

    @Test
    fun `pdf417 aztec data matrix рендерятся своими форматами`() {
        // Раньше они молча превращались в CODE_128 и не читались кассой
        assertNotNull(CodeRenderer.render("PDF417-PAYLOAD-123456", CodeType.BARCODE, "PDF417"))
        assertNotNull(CodeRenderer.render("AZTEC-PAYLOAD-123456", CodeType.BARCODE, "AZTEC"))
        assertNotNull(CodeRenderer.render("DM-PAYLOAD-123456", CodeType.BARCODE, "DATA_MATRIX"))
    }
}
