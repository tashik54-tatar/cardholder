package com.tashik54.cardholder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Card::class], version = 1, exportSchema = false)
abstract class CardHolderDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var instance: CardHolderDatabase? = null

        fun get(context: Context): CardHolderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CardHolderDatabase::class.java,
                    "cardholder.db",
                ).build().also { instance = it }
            }
    }
}
