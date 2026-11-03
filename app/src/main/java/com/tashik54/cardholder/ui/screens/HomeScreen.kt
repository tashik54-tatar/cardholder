package com.tashik54.cardholder.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.PhotoStore

/**
 * Главный экран: сетка карт. У карт с фото лицевой стороны — только фото,
 * у карт без фото — название магазина. Тап по карте открывает экран карты.
 */
@Composable
fun HomeScreen(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    onCardClick: (Card) -> Unit = {},
) {
    if (cards.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Нет карт",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Нажмите «+», чтобы добавить первую",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(cards, key = { it.id }) { card ->
            CardCell(
                card = card,
                onClick = { onCardClick(card) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun CardCell(card: Card, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f), // пропорция банковской карты
    ) {
        val front = remember(card.frontPhotoPath) {
            PhotoStore.load(card.frontPhotoPath, maxDim = 512)
        }
        val back = remember(card.backPhotoPath) {
            PhotoStore.load(card.backPhotoPath, maxDim = 512)
        }
        when {
            // Карты без кода (только фото): обе стороны рядом
            card.codeValue.isBlank() && (front != null || back != null) -> Row {
                if (front != null) {
                    Image(
                        bitmap = front.asImageBitmap(),
                        contentDescription = "${card.storeName} — лицевая сторона",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
                if (back != null) {
                    Image(
                        bitmap = back.asImageBitmap(),
                        contentDescription = "${card.storeName} — сторона со штрихкодом",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            }
            // Есть фото лицевой стороны — растягиваем по карточке
            front != null -> Image(
                bitmap = front.asImageBitmap(),
                contentDescription = card.storeName,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            // Иначе — название магазина
            else -> Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = card.storeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
