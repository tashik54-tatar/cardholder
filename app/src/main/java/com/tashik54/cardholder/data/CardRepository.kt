package com.tashik54.cardholder.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Единственная точка доступа к данным о картах: БД + фото-файлы.
 * Фото принадлежат карте, поэтому удаление карты и замена её фото
 * идут через репозиторий — одним согласованным порядком (сначала
 * запись в БД, потом файлы).
 */
class CardRepository(context: Context, private val dao: CardDao) {

    private val appContext = context.applicationContext

    fun observeAll(): Flow<List<Card>> = dao.observeAll()

    fun observeById(id: Long): Flow<Card?> = dao.observeById(id)

    suspend fun add(card: Card): Long = dao.insert(card)

    suspend fun update(card: Card) = dao.update(card)

    /** Удаляет карту из БД, затем её фото-файлы (сбой БД не оставит карту без фото). */
    suspend fun deleteCardWithPhotos(card: Card) {
        dao.delete(card)
        PhotoStore.delete(appContext, card.frontPhotoPath)
        PhotoStore.delete(appContext, card.backPhotoPath)
    }

    /**
     * Импорт CSV: дубликаты (та же пара «магазин + значение кода»,
     * без учёта регистра и пробелов) пропускаются, новые вставляются
     * одной транзакцией.
     */
    suspend fun importCards(cards: List<Card>): ImportResult {
        val existingKeys = dao.getAll().map(::keyOf).toHashSet()
        val fresh = cards.filter { keyOf(it) !in existingKeys }
        if (fresh.isNotEmpty()) {
            // Ключи новых тоже защищают от дублей внутри самого файла:
            // Set.add вернёт false на повторном ключе
            val seen = HashSet(existingKeys)
            val unique = fresh.filter { seen.add(keyOf(it)) }
            dao.insertAll(unique)
            return ImportResult(unique.size, cards.size - unique.size)
        }
        return ImportResult(0, cards.size)
    }

    private fun keyOf(card: Card): Pair<String, String> =
        card.storeName.trim().lowercase() to card.codeValue.trim()

    data class ImportResult(val imported: Int, val duplicates: Int)

    companion object {
        fun from(context: Context): CardRepository =
            CardRepository(context, CardHolderDatabase.get(context).cardDao())
    }
}
