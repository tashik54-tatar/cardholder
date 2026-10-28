package com.tashik54.cardholder.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.CodeRenderer
import com.tashik54.cardholder.ui.CardDetailViewModel

/**
 * Экран карты: отрисованный штрихкод или QR крупно на белом фоне —
 * чтобы кассовый сканер уверенно читал с экрана.
 */
@Composable
fun CardScreen(
    cardId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: CardDetailViewModel =
        viewModel(
            key = "card_$cardId",
            factory = CardDetailViewModel.factory(CardRepository.from(context), cardId),
        )
    val card by viewModel.card.collectAsStateWithLifecycle()

    val current = card ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = current.storeName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        val bitmap = remember(current.id, current.codeValue, current.codeType, current.barcodeFormat) {
            CodeRenderer.render(current.codeValue, current.codeType, current.barcodeFormat)
        }

        CodePanel(bitmap, current)

        Spacer(Modifier.height(16.dp))

        Text(
            text = current.codeValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CodePanel(bitmap: Bitmap, card: Card) {
    val isQr = card.codeType == CodeType.QR
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "${if (isQr) "QR-код" else "Штрихкод"} карты ${card.storeName}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isQr) Modifier.aspectRatio(1f) else Modifier.height(160.dp),
                )
                .background(
                    androidx.compose.ui.graphics.Color.White,
                    RoundedCornerShape(20.dp),
                )
                .padding(16.dp),
        )
    }
}
