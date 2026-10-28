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

    /** Сохраняет карту и вызывает onDone после записи в БД. */
    fun save(
        storeName: String,
        codeType: CodeType,
        codeValue: String,
        barcodeFormat: String? = null,
        frontPhotoPath: String? = null,
        backPhotoPath: String? = null,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            repository.add(
                Card(
                    storeName = storeName.trim(),
                    codeType = codeType,
                    codeValue = codeValue.trim(),
                    barcodeFormat = barcodeFormat,
                    frontPhotoPath = frontPhotoPath,
                    backPhotoPath = backPhotoPath,
                ),
            )
            onDone()
        }
    }

    companion object {
        fun factory(repository: CardRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddCardViewModel(repository) }
        }
    }
}
