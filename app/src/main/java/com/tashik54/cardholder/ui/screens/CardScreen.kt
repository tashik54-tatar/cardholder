package com.tashik54.cardholder.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.CodeRenderer
import com.tashik54.cardholder.data.PhotoStore
import com.tashik54.cardholder.ui.CardDetailViewModel
import java.io.File
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
    var captureFile by remember { mutableStateOf<File?>(null) }

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

    fun onPhotoTaken(file: File) {
        scope.launch {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val bitmap = withContext(Dispatchers.IO) { PhotoStore.loadFromUri(context, uri) }
            if (bitmap == null) {
                capturingSide = null
            } else {
                pendingCropBitmap = bitmap
                croppingSide = capturingSide
            }
            file.delete()
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val file = captureFile
        if (ok && file != null) onPhotoTaken(file) else capturingSide = null
    }

    fun startCapture(side: String) {
        capturingSide = side
        val file = PhotoStore.newCaptureFile(context)
        captureFile = file
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
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

        if (current.frontPhotoPath != null || current.backPhotoPath != null) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoThumb("Лицевая сторона", current.frontPhotoPath, Modifier.weight(1f))
                PhotoThumb("Оборотная сторона", current.backPhotoPath, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            OutlinedButton(
                onClick = { startCapture("front") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.padding(start = 6.dp))
                Text("Фото лицевой")
            }
            OutlinedButton(
                onClick = { startCapture("back") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.padding(start = 6.dp))
                Text("Фото оборотной")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PhotoThumb(label: String, path: String?, modifier: Modifier = Modifier) {
    val photo = remember(path) { PhotoStore.load(path, maxDim = 512) }
    if (photo != null) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.586f)
                    .background(Color.White, RoundedCornerShape(16.dp)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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
