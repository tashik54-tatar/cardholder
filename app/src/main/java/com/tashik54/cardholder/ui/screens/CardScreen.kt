package com.tashik54.cardholder.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tashik54.cardholder.R
import com.tashik54.cardholder.data.AppSettings
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.ui.CardDetailViewModel
import com.tashik54.cardholder.ui.rememberCodeBitmap
import com.tashik54.cardholder.ui.rememberPhoto

/**
 * Экран карты: отрисованный штрихкод или QR крупно на белом фоне —
 * чтобы кассовый сканер уверенно читал с экрана. Карандаш открывает
 * полную форму редактирования. Фото сторон — по настройке.
 */
@Composable
fun CardScreen(
    cardId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEdit: (Card) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: CardDetailViewModel =
        viewModel(
            key = "card_$cardId",
            factory = CardDetailViewModel.factory(CardRepository.from(context), cardId),
        )
    val card by viewModel.card.collectAsStateWithLifecycle()

    val current = card ?: return

    BackHandler(enabled = true) { onBack() }

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
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = current.storeName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onEdit(current) }) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        val hasCodeValue = current.codeValue.isNotBlank()
        if (hasCodeValue) {
            // Рендер асинхронный; если значение закодировать нельзя
            // (например, кириллица для штрихкода) — честное сообщение
            // вместо падания экрана.
            val bitmap = rememberCodeBitmap(
                value = current.codeValue,
                codeType = current.codeType,
                barcodeFormat = current.barcodeFormat,
                width = 1080,
                height = 360,
            )
            if (bitmap != null) {
                var codeFullscreen by rememberSaveable { mutableStateOf(false) }
                CodePanel(bitmap, current, onClick = { codeFullscreen = true })
                if (codeFullscreen) {
                    FullscreenCodeDialog(
                        card = current,
                        onDismiss = { codeFullscreen = false },
                        fallback = bitmap,
                    )
                }

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
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.code_render_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                Text(
                    text = current.codeValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }

        // Фото сторон: по настройке скрыты за кнопкой; тап по фото — на весь экран
        if (current.frontPhotoPath != null || current.backPhotoPath != null) {
            val hidePhotos = AppSettings.hidePhotosOnCardPage.value
            var photosOpen by rememberSaveable { mutableStateOf(!hidePhotos) }
            if (hidePhotos) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { photosOpen = !photosOpen },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        stringResource(
                            if (photosOpen) R.string.hide_photos else R.string.show_photos,
                        ),
                    )
                }
            }
            AnimatedVisibility(
                visible = photosOpen || !hidePhotos,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var viewingPath by remember { mutableStateOf<String?>(null) }
                    PhotoView(
                        label = stringResource(R.string.photo_front_label),
                        path = current.frontPhotoPath,
                        onClick = { current.frontPhotoPath?.let { viewingPath = it } },
                        modifier = Modifier.weight(1f),
                    )
                    PhotoView(
                        label = stringResource(R.string.photo_back_label),
                        path = current.backPhotoPath,
                        onClick = { current.backPhotoPath?.let { viewingPath = it } },
                        modifier = Modifier.weight(1f),
                    )
                    viewingPath?.let { path ->
                        PhotoViewerDialog(
                            label = if (path == current.frontPhotoPath) {
                                stringResource(R.string.photo_front_label)
                            } else {
                                stringResource(R.string.photo_back_label)
                            },
                            path = path,
                            onDismiss = { viewingPath = null },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PhotoView(
    label: String,
    path: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Декод JPEG — асинхронно, вне главного потока
    val photo = rememberPhoto(path, maxDim = 512)
    if (photo != null) {
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(1.586f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
        )
    } else {
        Box(modifier)
    }
}

/**
 * Просмотр фото на весь экран: чёрный фон, картинка целиком,
 * масштабирование щипком, тап — закрыть.
 */
@Composable
private fun PhotoViewerDialog(
    label: String,
    path: String,
    onDismiss: () -> Unit,
) {
    val photo = rememberPhoto(path, maxDim = 2048) ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            offset = if (scale > 1f) offset + panChange else Offset.Zero
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() }, onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CodePanel(
    bitmap: android.graphics.Bitmap,
    card: Card,
    onClick: () -> Unit = {},
) {
    val isQr = card.codeType == CodeType.QR
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(
                if (isQr) R.string.cd_code_qr else R.string.cd_code_barcode,
                card.storeName,
            ),
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
                .padding(16.dp)
                .clickable(onClick = onClick),
        )
    }
}

/**
 * Код на весь экран — крупно для кассового сканера. Тап — закрыть.
 * Если крупный рендер не удался (значение не кодируется), показываем
 * обычный панельный bitmap — он по построению существует.
 */
@Composable
private fun FullscreenCodeDialog(
    card: Card,
    onDismiss: () -> Unit,
    fallback: android.graphics.Bitmap? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val isQr = card.codeType == CodeType.QR
        val big = rememberCodeBitmap(
            value = card.codeValue,
            codeType = card.codeType,
            barcodeFormat = card.barcodeFormat,
            width = if (isQr) 1400 else 1800,
            height = if (isQr) 1400 else 600,
        ) ?: fallback
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        ) {
            if (big != null) {
                Image(
                    bitmap = big.asImageBitmap(),
                    contentDescription = stringResource(
                        if (isQr) R.string.cd_code_qr else R.string.cd_code_barcode,
                        card.storeName,
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                )
            }
        }
    }
}
