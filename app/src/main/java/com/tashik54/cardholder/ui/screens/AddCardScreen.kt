package com.tashik54.cardholder.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.tashik54.cardholder.data.BarcodeDecoder
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeRenderer
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.PhotoStore
import com.tashik54.cardholder.data.barcodeFormatName
import com.tashik54.cardholder.data.resolveCodeType
import com.tashik54.cardholder.ui.AddCardViewModel
import com.tashik54.cardholder.ui.scan.PhotoCaptureScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Полупрозрачные подложки полей и плиток — цвета темы (работают в обеих темах)
private val PanelColor: Color @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
private val PanelColorDim: Color @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)

/**
 * Форма новой карты: название магазина, фото сторон и значение кода.
 *
 * Фото: две плитки — лицевая сторона и сторона со штрихкодом. Тап открывает
 * камеру с подсказкой; после снимка возвращаемся в форму, где программа
 * сама распознала штрихкод (и сама поняла, какая сторона где: фото с кодом —
 * оборот, без кода — лицевая). Повторный тап по плитке переснимает сторону,
 * старое фото заменяется. Тип кода не выбирается вручную.
 */
@Composable
fun AddCardScreen(
    modifier: Modifier = Modifier,
    onSaved: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: AddCardViewModel =
        viewModel(factory = AddCardViewModel.factory(CardRepository.from(context)))

    var storeName by rememberSaveable { mutableStateOf("") }
    var codeValue by rememberSaveable { mutableStateOf("") }
    var detectedFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var frontPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var backPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    // Какую сторону снимаем: "front" / "back"
    var capturingSide by rememberSaveable { mutableStateOf<String?>(null) }
    // Для какой стороны выбираем источник фото (камера/галерея)
    var choosingSide by rememberSaveable { mutableStateOf<String?>(null) }
    // Стадия после снимка: обрезка фото
    var croppingSide by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingBarcode by remember { mutableStateOf<Barcode?>(null) }

    // Код теперь необязателен: карту можно сохранить только с фото сторон
    val canSave = storeName.isNotBlank()

    fun cancel() {
        // Несохранённые фото не должны оставаться файлами-сиротами
        PhotoStore.delete(frontPhotoPath)
        PhotoStore.delete(backPhotoPath)
        onCancel()
    }

    // «Назад» закрывает форму (слой камеры/обрезки — это диалоги,
    // они обрабатывают «назад» сами)
    BackHandler(enabled = true) { cancel() }

    // Для какой стороны ждем картинку из галереи
    var gallerySideToPick by remember { mutableStateOf<String?>(null) }

    val pickGalleryImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val side = gallerySideToPick
        gallerySideToPick = null
        if (uri != null && side != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { PhotoStore.loadFromUri(context, uri) }
                if (bitmap == null) {
                    Toast.makeText(context, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show()
                } else {
                    pendingBarcode = BarcodeDecoder.decodeBestEffort(context, bitmap)
                    pendingCropBitmap = bitmap
                    croppingSide = side
                }
            }
        }
    }

    fun useGallery(side: String) {
        gallerySideToPick = side
        pickGalleryImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // Тип кода и предпросмотр считаются из текущего значения
    val resolvedType = resolveCodeType(codeValue, detectedFormat)
    val previewBitmap = remember(codeValue) {
        if (codeValue.isBlank()) null
        else try {
            CodeRenderer.render(codeValue, resolvedType, detectedFormat, width = 720, height = 240)
        } catch (_: Exception) {
            null
        }
    }

    fun applyBarcode(barcode: Barcode) {
        val value = barcode.rawValue ?: return
        codeValue = value
        detectedFormat = barcodeFormatName(barcode.format)
    }

    // После обрезки: фото — на своё место (старое заменяется), код — в форму
    fun onCropDone(side: String?, cropped: Bitmap?) {
        val target = side ?: return
        if (cropped != null) {
            scope.launch {
                // Код не нашёлся на полном кадре — пробуем распознать его на обрезанном
                var barcode = pendingBarcode
                if (barcode == null) {
                    barcode = BarcodeDecoder.decodeBestEffort(context, cropped)
                }
                // Фото с кодом всегда считается оборотом, без кода — лицевой
                val goesToBack = barcode != null || target == "back"
                val path = withContext(Dispatchers.IO) { PhotoStore.save(context, cropped) }
                if (goesToBack) {
                    PhotoStore.delete(backPhotoPath?.takeIf { it != path })
                    backPhotoPath = path
                    barcode?.let { applyBarcode(it) }
                        ?: Toast.makeText(
                            context,
                            "Штрихкод на фото не найден — введите код вручную",
                            Toast.LENGTH_LONG,
                        ).show()
                } else {
                    PhotoStore.delete(frontPhotoPath?.takeIf { it != path })
                    frontPhotoPath = path
                }
            }
        }
        pendingBarcode = null
        pendingCropBitmap = null
        croppingSide = null
    }

    // Съёмка стороны карты в окне камеры приложения, с подсказкой
    capturingSide?.let { side ->
        PhotoCaptureScreen(
            instruction = if (side == "front") {
                "Сфотографируйте лицевую сторону карты"
            } else {
                "Сфотографируйте сторону со штрихкодом"
            },
            onCaptured = { bitmap ->
                capturingSide = null
                scope.launch {
                    pendingBarcode = BarcodeDecoder.decodeBestEffort(context, bitmap)
                    pendingCropBitmap = bitmap
                    croppingSide = side
                }
            },
            onClose = { capturingSide = null },
        )
        return
    }

    // Обрезка снимка
    croppingSide?.let { side ->
        PhotoCropScreen(
            bitmap = pendingCropBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            onDone = { cropped -> onCropDone(side, cropped) },
        )
        return
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val barcode = BarcodeDecoder.decode(context, uri)
            if (barcode != null) {
                applyBarcode(barcode)
            } else {
                Toast.makeText(context, "Код на изображении не найден", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Выбор источника фото: камера или галерея
    choosingSide?.let { side ->
        AlertDialog(
            onDismissRequest = { choosingSide = null },
            title = { Text("Фото стороны") },
            text = {
                Text(
                    if (side == "front") {
                        "Лицевая сторона: сфотографировать карту или взять готовую картинку из телефона?"
                    } else {
                        "Сторона со штрихкодом: сфотографировать или взять готовую картинку (можно скриншот)?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        choosingSide = null
                        capturingSide = side
                    },
                ) { Text("Сделать фото") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        choosingSide = null
                        useGallery(side)
                    },
                ) { Text("Из галереи") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = "Новая карта",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            // Крестик — отмена, галочка — сохранить
            IconButton(onClick = { cancel() }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Отмена",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = {
                viewModel.save(
                    storeName = storeName,
                    codeType = resolvedType,
                    codeValue = codeValue,
                    barcodeFormat = detectedFormat?.takeIf { resolvedType != CodeType.QR },
                    frontPhotoPath = frontPhotoPath,
                    backPhotoPath = backPhotoPath,
                    onDone = onSaved,
                )
            }, enabled = canSave) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Сохранить",
                    tint = if (canSave) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
        }

        OutlinedTextField(
            value = storeName,
            onValueChange = { storeName = it },
            label = { Text("Название магазина") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PanelColor,
                unfocusedContainerColor = PanelColorDim,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Фото сторон",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoSlot(
                label = "Лицевая сторона",
                hint = "Сфотографировать лицевую",
                photoPath = frontPhotoPath,
                onShoot = { choosingSide = "front" },
                onClear = {
                    PhotoStore.delete(frontPhotoPath)
                    frontPhotoPath = null
                },
                modifier = Modifier.weight(1f),
            )
            PhotoSlot(
                label = "Сторона со штрихкодом",
                hint = "Сфотографировать штрихкод",
                photoPath = backPhotoPath,
                onShoot = { choosingSide = "back" },
                onClear = {
                    PhotoStore.delete(backPhotoPath)
                    backPhotoPath = null
                },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Штрихкод с фото распознаётся автоматически",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = codeValue,
            onValueChange = {
                codeValue = it
                // значение поменяли вручную — распознанный формат больше не соответствует
                if (detectedFormat != null) detectedFormat = null
            },
            label = { Text("Значение кода (номер карты или ссылка)") },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PanelColor,
                unfocusedContainerColor = PanelColorDim,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Предпросмотр: код картинкой сразу видно, что именно сохранится
        AnimatedVisibility(
            visible = previewBitmap != null,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val shown = previewBitmap
                if (shown != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "Предпросмотр кода",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (resolvedType == CodeType.QR) {
                                    Modifier.aspectRatio(1f)
                                } else {
                                    Modifier.height(120.dp)
                                },
                            )
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                    )
                    if (detectedFormat != null) {
                        Text(
                            text = "Код распознан: $detectedFormat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Код можно ввести и без фото — расшифровать его из файла с картинкой
        OutlinedButton(
            onClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.padding(start = 6.dp))
            Text("Код из файла")
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Плитка стороны карты: превью фото или заглушка. Тап по любой —
 * переснять сторону (старое фото заменится), крестик — убрать фото.
 */
@Composable
private fun PhotoSlot(
    label: String,
    hint: String,
    photoPath: String?,
    onShoot: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .background(PanelColorDim, RoundedCornerShape(16.dp))
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
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Убрать фото",
                        tint = Color(0xFF1C1B22),
                    )
                }
                // Подсказка про повторный тап
                Text(
                    text = "Нажмите, чтобы заменить",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1C1B22),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
