package com.tashik54.cardholder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): Card?

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observeById(id: Long): Flow<Card?>

    @Insert
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)
}
