package com.tashik54.cardholder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Тип кода на карте. */
enum class CodeType { QR, BARCODE }

/**
 * Скидочная карта.
 *
 * Фото сторон хранятся файлами в приватном каталоге приложения,
 * в БД — только пути (лицевая сторона опциональна).
 */
@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: String,
    val codeType: CodeType,
    /** Формат штрихкода (CODE_128, EAN_13…); для QR — null. */
    val barcodeFormat: String? = null,
    val codeValue: String,
    val frontPhotoPath: String? = null,
    val backPhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
