package com.tashik54.cardholder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeType
import kotlinx.coroutines.launch

class AddCardViewModel(private val repository: CardRepository) : ViewModel() {

    /** Сохраняет карту (новую или существующую по cardId) и вызывает onDone. */
    fun save(
        storeName: String,
        codeType: CodeType,
        codeValue: String,
        barcodeFormat: String? = null,
        frontPhotoPath: String? = null,
        backPhotoPath: String? = null,
        cardId: Long = 0,
        createdAt: Long? = null,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val card = Card(
                id = cardId,
                storeName = storeName.trim(),
                codeType = codeType,
                codeValue = codeValue.trim(),
                barcodeFormat = barcodeFormat,
                frontPhotoPath = frontPhotoPath,
                backPhotoPath = backPhotoPath,
                createdAt = createdAt ?: System.currentTimeMillis(),
            )
            if (cardId != 0L) {
                repository.update(card)
            } else {
                repository.add(card)
            }
            onDone()
        }
    }

    /**
     * Удаляет карту и её фото (порядок согласует репозиторий:
     * сначала запись в БД, потом файлы) и вызывает onDone.
     */
    fun delete(card: Card, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCardWithPhotos(card)
            onDone()
        }
    }

    companion object {
        fun factory(repository: CardRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddCardViewModel(repository) }
        }
    }
}
