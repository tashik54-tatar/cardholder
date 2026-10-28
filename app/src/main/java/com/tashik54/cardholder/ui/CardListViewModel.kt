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

class CardListViewModel(repository: CardRepository) : ViewModel() {

    /** Все карты, новые сверху. */
    val cards: StateFlow<List<Card>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: CardRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CardListViewModel(repository) }
        }
    }
}
