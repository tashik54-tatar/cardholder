package com.tashik54.cardholder

import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.CsvCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvCodecTest {

    @Test
    fun `экспорт и обратный разбор сохраняют данные`() {
        val cards = listOf(
            Card(storeName = "Пятёрочка", codeType = CodeType.BARCODE, barcodeFormat = "EAN_13", codeValue = "4600123456789", createdAt = 1_700_000_000_000),
            Card(storeName = "Кофейня у дома", codeType = CodeType.QR, codeValue = "https://coffee.example/p/42"),
        )
        val parsed = CsvCodec.parse(CsvCodec.export(cards))
        assertEquals(0, parsed.skippedRows)
        assertEquals(2, parsed.cards.size)
        assertEquals("Пятёрочка", parsed.cards[0].storeName)
        assertEquals(CodeType.BARCODE, parsed.cards[0].codeType)
        assertEquals("EAN_13", parsed.cards[0].barcodeFormat)
        assertEquals("4600123456789", parsed.cards[0].codeValue)
        assertEquals(1_700_000_000_000, parsed.cards[0].createdAt)
        assertEquals(CodeType.QR, parsed.cards[1].codeType)
        assertEquals(null, parsed.cards[1].barcodeFormat)
        // id и фото при импорте всегда сбрасываются
        assertEquals(0, parsed.cards[0].id)
        assertEquals(null, parsed.cards[0].frontPhotoPath)
    }

    @Test
    fun `спецсимволы в полях не ломают разбор`() {
        val tricky = "Магазин \"У Ашота\"; №1"
        val cards = listOf(
            Card(storeName = tricky, codeType = CodeType.BARCODE, codeValue = "ABC;123\n456"),
        )
        val parsed = CsvCodec.parse(CsvCodec.export(cards))
        assertEquals(1, parsed.cards.size)
        assertEquals(tricky, parsed.cards[0].storeName)
        assertEquals("ABC;123\n456", parsed.cards[0].codeValue)
    }

    @Test
    fun `парсинг без заголовка и с запятой`() {
        val csv = "Азбука вкуса,BARCODE,EAN_13,1234567890123,1700000000000"
        val parsed = CsvCodec.parse(csv)
        assertEquals(1, parsed.cards.size)
        assertEquals("Азбука вкуса", parsed.cards[0].storeName)
        assertEquals("EAN_13", parsed.cards[0].barcodeFormat)
    }

    @Test
    fun `BOM и пустые строки игнорируются`() {
        val csv = "\uFEFFname;codeType;barcodeFormat;codeValue;createdAt\r\n\r\nЛента;QR;;https://lenta.example\r\n"
        val parsed = CsvCodec.parse(csv)
        assertEquals(1, parsed.cards.size)
        assertEquals("Лента", parsed.cards[0].storeName)
        assertEquals("https://lenta.example", parsed.cards[0].codeValue)
    }

    @Test
    fun `битые строки пропускаются со счётчиком`() {
        val csv = "name;codeType;barcodeFormat;codeValue;createdAt\nОК;BARCODE;;123\n\nПлохая;BARCODE"
        val parsed = CsvCodec.parse(csv)
        assertEquals(1, parsed.cards.size)
        assertEquals("ОК", parsed.cards[0].storeName)
        assertEquals(1, parsed.skippedRows)
    }

    @Test
    fun `карта без кода переезжает как пустое значение`() {
        val cards = listOf(Card(storeName = "Только фото", codeType = CodeType.BARCODE, codeValue = ""))
        val parsed = CsvCodec.parse(CsvCodec.export(cards))
        assertEquals(1, parsed.cards.size)
        assertEquals("", parsed.cards[0].codeValue)
    }
}
