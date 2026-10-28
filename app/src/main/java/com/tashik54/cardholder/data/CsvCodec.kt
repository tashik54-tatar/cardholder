package com.tashik54.cardholder.data

/**
 * Кодирование/разбор CSV для резервных копий карт.
 *
 * Формат: UTF-8 с BOM (для Excel), разделитель «;» (понятен русскому Excel),
 * поля с кавычками по RFC 4180. При разборе разделитель определяется
 * автоматически («;» или «»), заголовок и BOM допускаются.
 */
object CsvCodec {

    private val HEADER = listOf("name", "codeType", "barcodeFormat", "codeValue", "createdAt")
    private const val BOM = "\uFEFF"

    /** Сериализация карт в CSV-текст. Фото сторон в CSV не переносятся. */
    fun export(cards: List<Card>): String = buildString {
        append(BOM)
        append(HEADER.joinToString(";"))
        append("\r\n")
        for (card in cards) {
            append(
                listOf(
                    card.storeName,
                    card.codeType.name,
                    card.barcodeFormat ?: "",
                    card.codeValue,
                    card.createdAt.toString(),
                ).joinToString(";") { escape(it) },
            )
            append("\r\n")
        }
    }

    private fun escape(field: String): String =
        if (field.isEmpty()) {
            ""
        } else if (field.any { it == ';' || it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }

    /**
     * Разбор CSV в список карт (id и фото не переносятся — присваиваются заново).
     * Некорректные строки пропускаются. Возвращает карты и число пропущенных строк.
     */
    fun parse(text: String): ParseResult {
        val rows = tokenize(text)
        if (rows.isEmpty()) return ParseResult(emptyList(), 0)

        // Заголовок: первая ячейка "name" (наш формат) — пропускаем
        val dataRows = if (rows.first().firstOrNull()?.equals(HEADER[0], ignoreCase = true) == true) {
            rows.drop(1)
        } else {
            rows
        }

        val cards = ArrayList<Card>(dataRows.size)
        var skipped = 0
        for (row in dataRows) {
            if (row.size < 4 || row[0].isBlank()) {
                skipped++
                continue
            }
            cards.add(
                Card(
                    storeName = row[0].trim(),
                    codeType = if (row[1].trim().equals("QR", ignoreCase = true)) CodeType.QR else CodeType.BARCODE,
                    barcodeFormat = row[2].trim().takeIf { it.isNotEmpty() },
                    codeValue = row[3].trim(),
                    createdAt = row.getOrNull(4)?.trim()?.toLongOrNull() ?: System.currentTimeMillis(),
                ),
            )
        }
        return ParseResult(cards, skipped)
    }

    data class ParseResult(val cards: List<Card>, val skippedRows: Int)

    /** Разбор строк CSV: кавычки, удвоенные кавычки, переносы внутри поля. */
    private fun tokenize(text: String): List<List<String>> {
        val content = text.removePrefix(BOM)
        val delimiter = if (content.lineSequence().firstOrNull()?.contains(';') == true ||
            content.contains(';')
        ) {
            ';'
        } else {
            ','
        }

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field = StringBuilder()
        }

        fun endRow() {
            endField()
            // Пропускаем полностью пустые строки
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = ArrayList()
        }

        while (i < content.length) {
            val ch = content[i]
            when {
                inQuotes -> when (ch) {
                    '"' -> {
                        if (i + 1 < content.length && content[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(ch)
                }
                ch == '"' && field.isEmpty() -> inQuotes = true
                ch == delimiter -> endField()
                ch == '\r' -> { /* съедаем, конец строки ловим по \n */ }
                ch == '\n' -> endRow()
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
