package com.tashik54.cardholder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CardDetailViewModel(repository: CardRepository, cardId: Long) : ViewModel() {

    val card: StateFlow<Card?> = repository.observeById(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(repository: CardRepository, cardId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { CardDetailViewModel(repository, cardId) }
            }
    }
}
