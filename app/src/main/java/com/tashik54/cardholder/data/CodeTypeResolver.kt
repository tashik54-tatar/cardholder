package com.tashik54.cardholder.data

/**
 * Определение типа кода без участия пользователя: сканер сам распознаёт
 * формат, а при ручном вводе тип угадывается по значению.
 */
fun resolveCodeType(value: String, formatName: String?): CodeType {
    if (formatName == "QR_CODE") return CodeType.QR
    if (formatName != null) return CodeType.BARCODE
    // Ручной ввод: URL и похожие на ссылку значения — QR (телефонные
    // сканеры ждут от QR именно ссылку), остальное — штрихкод.
    val v = value.trim().lowercase()
    val looksLikeUrl = v.startsWith("http://") ||
        v.startsWith("https://") ||
        v.startsWith("www.") ||
        v.contains("://")
    return if (looksLikeUrl) CodeType.QR else CodeType.BARCODE
}
