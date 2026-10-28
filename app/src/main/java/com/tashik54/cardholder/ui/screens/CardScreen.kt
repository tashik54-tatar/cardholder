package com.tashik54.cardholder.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.CodeRenderer
import com.tashik54.cardholder.data.PhotoStore
import com.tashik54.cardholder.ui.CardDetailViewModel

/**
 * Экран карты: отрисованный штрихкод или QR крупно на белом фоне —
 * чтобы кассовый сканер уверенно читал с экрана. Ниже — фото сторон
 * (просмотр). Карандаш в шапке включает редактирование: название,
 * удаление карты. Фото снимаются только при добавлении карты.
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

    var editing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (editing) {
            EditBar(
                card = current,
                onSave = { name ->
                    if (name.isNotBlank() && name != current.storeName) {
                        viewModel.update(current.copy(storeName = name.trim()))
                    }
                    editing = false
                },
                onDelete = {
                    viewModel.delete(current)
                    onBack()
                },
                onCancel = { editing = false },
            )
        } else {
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
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { editing = true }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Редактировать",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            val bitmap = remember(current.id, current.codeValue, current.codeType, current.barcodeFormat) {
                CodeRenderer.render(current.codeValue, current.codeType, current.barcodeFormat)
            }

            var codeFullscreen by rememberSaveable { mutableStateOf(false) }
            CodePanel(bitmap, current, onClick = { codeFullscreen = true })
            if (codeFullscreen) {
                FullscreenCodeDialog(
                    card = current,
                    onDismiss = { codeFullscreen = false },
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

            // Фото сторон: тап — просмотр на весь экран; подписей нет
            if (current.frontPhotoPath != null || current.backPhotoPath != null) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var viewingPath by remember { mutableStateOf<String?>(null) }
                    PhotoView(
                        label = "Лицевая сторона",
                        path = current.frontPhotoPath,
                        onClick = { current.frontPhotoPath?.let { viewingPath = it } },
                        modifier = Modifier.weight(1f),
                    )
                    PhotoView(
                        label = "Сторона со штрихкодом",
                        path = current.backPhotoPath,
                        onClick = { current.backPhotoPath?.let { viewingPath = it } },
                        modifier = Modifier.weight(1f),
                    )
                    viewingPath?.let { path ->
                        PhotoViewerDialog(
                            label = if (path == current.frontPhotoPath) "Лицевая сторона" else "Сторона со штрихкодом",
                            path = path,
                            onDismiss = { viewingPath = null },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Режим редактирования: переименование и удаление карты. */
@Composable
private fun EditBar(
    card: Card,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(card.storeName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Отмена",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Редактирование",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Сохранить",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название магазина") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(name) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.7f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDelete,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFC65B4E),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Удалить карту", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PhotoView(
    label: String,
    path: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = remember(path) { PhotoStore.load(path, maxDim = 512) }
    if (photo != null) {
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(1.586f)
                .background(Color.White, RoundedCornerShape(16.dp))
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
    val photo = remember(path) { PhotoStore.load(path, maxDim = 2048) } ?: return

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
                    contentDescription = "Закрыть",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CodePanel(bitmap: Bitmap, card: Card, onClick: () -> Unit = {}) {
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
                .padding(16.dp)
                .clickable(onClick = onClick),
        )
    }
}

/**
 * Код на весь экран в горизонтальной ориентации — крупно для кассового
 * сканера. Тап — закрыть, ориентация возвращается при выходе.
 */
@Composable
private fun FullscreenCodeDialog(
    card: Card,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val isQr = card.codeType == CodeType.QR
        val big = remember(card.id, card.codeValue, card.codeType, card.barcodeFormat) {
            CodeRenderer.render(
                card.codeValue,
                card.codeType,
                card.barcodeFormat,
                width = if (isQr) 1400 else 1800,
                height = if (isQr) 1400 else 600,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        ) {
            Image(
                bitmap = big.asImageBitmap(),
                contentDescription = "${if (isQr) "QR-код" else "Штрихкод"} карты ${card.storeName}",
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

private fun Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
