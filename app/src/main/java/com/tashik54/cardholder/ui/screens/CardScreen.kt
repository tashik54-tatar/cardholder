package com.tashik54.cardholder.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.tashik54.cardholder.data.PhotoStore
import com.tashik54.cardholder.ui.CardDetailViewModel
import com.tashik54.cardholder.ui.scan.PhotoCaptureScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран карты: отрисованный штрихкод или QR крупно на белом фоне —
 * чтобы кассовый сканер уверенно читал с экрана. Ниже — фото лицевой
 * и оборотной сторон (можно снять/заменить, с ручной обрезкой).
 */
@Composable
fun CardScreen(
    cardId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: CardDetailViewModel =
        viewModel(
            key = "card_$cardId",
            factory = CardDetailViewModel.factory(CardRepository.from(context), cardId),
        )
    val card by viewModel.card.collectAsStateWithLifecycle()

    val current = card ?: return

    var capturingSide by rememberSaveable { mutableStateOf<String?>(null) }
    var croppingSide by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun savePhoto(side: String, bitmap: Bitmap) {
        scope.launch {
            val path = withContext(Dispatchers.IO) { PhotoStore.save(context, bitmap) }
            val front = if (side == "front") path else current.frontPhotoPath
            val back = if (side == "back") path else current.backPhotoPath
            if (side == "front") PhotoStore.delete(current.frontPhotoPath?.takeIf { it != path })
            else PhotoStore.delete(current.backPhotoPath?.takeIf { it != path })
            viewModel.update(current.copy(frontPhotoPath = front, backPhotoPath = back))
        }
    }

    capturingSide?.let { side ->
        PhotoCaptureScreen(
            instruction = if (side == "front") {
                "Сфотографируйте лицевую сторону карты"
            } else {
                "Сфотографируйте сторону со штрихкодом"
            },
            onCaptured = { bitmap ->
                capturingSide = null
                pendingCropBitmap = bitmap
                croppingSide = side
            },
            onClose = { capturingSide = null },
        )
        return
    }

    if (croppingSide != null) {
        PhotoCropScreen(
            bitmap = pendingCropBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            onDone = { cropped ->
                val side = croppingSide
                if (cropped != null && side != null) savePhoto(side, cropped)
                pendingCropBitmap = null
                croppingSide = null
            },
        )
        return
    }

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

        // Фото сторон: тап по плитке — снять/переснять (старое фото заменится)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            PhotoTile(
                label = "Лицевая сторона",
                hint = "Сфотографировать лицевую",
                photoPath = current.frontPhotoPath,
                onShoot = { capturingSide = "front" },
                modifier = Modifier.weight(1f),
            )
            PhotoTile(
                label = "Сторона со штрихкодом",
                hint = "Сфотографировать штрихкод",
                photoPath = current.backPhotoPath,
                onShoot = { capturingSide = "back" },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Плитка стороны карты: фото или заглушка с камерой; тап — снять. */
@Composable
private fun PhotoTile(
    label: String,
    hint: String,
    photoPath: String?,
    onShoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .clickable(onClick = onShoot),
        ) {
            val photo = remember(photoPath) { PhotoStore.load(photoPath, maxDim = 512) }
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (photoPath != null) "$label — нажмите, чтобы заменить" else hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
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
                    Color.White,
                    RoundedCornerShape(20.dp),
                )
                .padding(16.dp),
        )
    }
}
