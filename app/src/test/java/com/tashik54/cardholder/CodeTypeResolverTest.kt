package com.tashik54.cardholder

import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.resolveCodeType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Тип кода определяется сам, без выбора пользователем. */
class CodeTypeResolverTest {

    @Test
    fun `формат от сканера имеет приоритет`() {
        assertEquals(CodeType.QR, resolveCodeType("любая строка", "QR_CODE"))
        assertEquals(CodeType.BARCODE, resolveCodeType("4600000000000", "EAN_13"))
    }

    @Test
    fun `url при ручном вводе - это QR`() {
        assertEquals(CodeType.QR, resolveCodeType("https://example.com/coupon", null))
        assertEquals(CodeType.QR, resolveCodeType("WWW.EXAMPLE.COM", null))
        assertEquals(CodeType.QR, resolveCodeType("app://card/123", null))
    }

    @Test
    fun `номер карты при ручном вводе - это штрихкод`() {
        assertEquals(CodeType.BARCODE, resolveCodeType("4600123456789", null))
        assertEquals(CodeType.BARCODE, resolveCodeType("ABC-123456", null))
        assertEquals(CodeType.BARCODE, resolveCodeType("", null))
    }
}
