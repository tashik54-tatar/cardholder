package com.tashik54.cardholder.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Единственная точка доступа к данным о картах. */
class CardRepository(private val dao: CardDao) {

    fun observeAll(): Flow<List<Card>> = dao.observeAll()

    suspend fun add(card: Card): Long = dao.insert(card)

    suspend fun update(card: Card) = dao.update(card)

    suspend fun delete(card: Card) = dao.delete(card)

    companion object {
        fun from(context: Context): CardRepository =
            CardRepository(CardHolderDatabase.get(context).cardDao())
    }
}
