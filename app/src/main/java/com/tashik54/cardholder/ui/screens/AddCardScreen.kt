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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.tashik54.cardholder.R
import com.tashik54.cardholder.data.BarcodeDecoder
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.PhotoStore
import com.tashik54.cardholder.data.barcodeFormatName
import com.tashik54.cardholder.data.resolveCodeType
import com.tashik54.cardholder.ui.AddCardViewModel
import com.tashik54.cardholder.ui.rememberCodeBitmap
import com.tashik54.cardholder.ui.rememberPhoto
import com.tashik54.cardholder.ui.scan.PhotoCaptureScreen
import com.tashik54.cardholder.ui.theme.AccentDanger
import com.tashik54.cardholder.ui.theme.OnPhotoBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Полупрозрачные подложки полей и плиток — цвета темы (работают в обеих темах)
private val PanelColor: Color @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
private val PanelColorDim: Color @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)

/** Круглая кнопка в шапке формы: filled — залитая (главное действие), иначе с обводкой. */
@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    filled: Boolean,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(
                        if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                        },
                        CircleShape,
                    )
                } else {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        CircleShape,
                    )
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        content()
    }
}

/**
 * Форма новой карты: название магазина, фото сторон и значение кода.
 *
 * Фото: две плитки — лицевая сторона и сторона со штрихкодом. Тап открывает
 * камеру с подсказкой; после снимка сразу открывается обрезка (распознавание
 * кода ML Kit идёт параллельно и не блокирует интерфейс), обрезанное фото
 * возвращается в форму, где программа сама поняла, какая сторона где:
 * фото с кодом — оборот, без кода — лицевая. Повторный тап по плитке
 * переснимает сторону, старое фото заменяется. Тип кода не выбирается вручную.
 */
@Composable
fun AddCardScreen(
    modifier: Modifier = Modifier,
    existing: Card? = null,
    onSaved: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: AddCardViewModel =
        viewModel(factory = AddCardViewModel.factory(CardRepository.from(context)))

    var storeName by rememberSaveable { mutableStateOf(existing?.storeName ?: "") }
    var codeValue by rememberSaveable { mutableStateOf(existing?.codeValue ?: "") }
    var detectedFormat by rememberSaveable { mutableStateOf(existing?.barcodeFormat) }
    var frontPhotoPath by rememberSaveable { mutableStateOf(existing?.frontPhotoPath) }
    var backPhotoPath by rememberSaveable { mutableStateOf(existing?.backPhotoPath) }
    // Подтверждение удаления (только в режиме редактирования)
    var confirmDelete by remember { mutableStateOf(false) }
    // В режиме редактирования старые файлы фото удаляем только после сохранения
    val orphanPaths = remember { mutableStateListOf<String>() }

    // Какую сторону снимаем: "front" / "back"
    var capturingSide by rememberSaveable { mutableStateOf<String?>(null) }
    // Для какой стороны выбираем источник фото (камера/галерея)
    var choosingSide by rememberSaveable { mutableStateOf<String?>(null) }
    // Стадия после снимка: обрезка фото
    var croppingSide by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingBarcode by remember { mutableStateOf<Barcode?>(null) }
    // Для какой стороны ждём картинку из галереи (переживает поворот)
    var gallerySideToPick by rememberSaveable { mutableStateOf<String?>(null) }

    // Фото обрабатывается (декод/обрезка/сохранение файла) — сохранение
    // карты на это время заблокировано, иначе новый файл стал бы сиротой.
    var photoProcessing by rememberSaveable { mutableStateOf(false) }
    // Карта в сохранении: защита от двойного тапа по галочке (дубль в БД)
    var saving by rememberSaveable { mutableStateOf(false) }

    // Код теперь необязателен: карту можно сохранить только с фото сторон
    val canSave = storeName.isNotBlank() && !photoProcessing && !saving

    // Поворот посреди обрезки: croppingSide восстановлен из saved state,
    // а битмап (обычный remember) — нет; стадию сбрасываем, а не показываем
    // заглушку 1×1.
    LaunchedEffect(croppingSide, pendingCropBitmap) {
        if (croppingSide != null && pendingCropBitmap == null) croppingSide = null
    }

    // Тип кода и предпросмотр считаются из текущего значения.
    // В редактировании, пока значение кода не менялось, сохраняем исходный
    // тип как есть: QR-код нельзя «угадать» по значению (это не всегда URL),
    // а пересчёт превращал нетронутый QR в штрихкод.
    val resolvedType = if (existing != null && codeValue == existing.codeValue) {
        existing.codeType
    } else {
        resolveCodeType(codeValue, detectedFormat)
    }
    // Рендер асинхронный (Default-диспатчер) и пересчитывается при смене
    // значения, типа И формата — предпросмотр всегда совпадает с тем,
    // что уйдёт в БД.
    val previewBitmap = rememberCodeBitmap(
        value = codeValue,
        codeType = resolvedType,
        barcodeFormat = detectedFormat,
        width = 720,
        height = 240,
    )

    fun cancel() {
        // В новой карте несохранённые фото — сироты, удаляем.
        // В редактировании файлы принадлежат сохранённой карте — не трогаем
        // (включая файлы, снятые с карты крестиком: они в orphanPaths и
        // удалятся только после сохранения).
        if (existing == null) {
            PhotoStore.delete(context, frontPhotoPath)
            PhotoStore.delete(context, backPhotoPath)
        }
        onCancel()
    }

    fun saveCard() {
        if (!canSave) return
        saving = true
        val orphans = orphanPaths.toList()
        viewModel.save(
            storeName = storeName,
            codeType = resolvedType,
            codeValue = codeValue,
            barcodeFormat = detectedFormat?.takeIf { resolvedType != CodeType.QR },
            frontPhotoPath = frontPhotoPath,
            backPhotoPath = backPhotoPath,
            cardId = existing?.id ?: 0,
            createdAt = existing?.createdAt,
            onDone = {
                orphans.forEach { PhotoStore.delete(context, it) }
                saving = false
                onSaved()
            },
        )
    }

    // «Назад» закрывает форму (слой камеры/обрезки — это диалоги,
    // они обрабатывают «назад» сами)
    BackHandler(enabled = true) { cancel() }

    val pickGalleryImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val side = gallerySideToPick
        gallerySideToPick = null
        if (uri != null && side != null) {
            photoProcessing = true
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { PhotoStore.loadFromUri(context, uri) }
                if (bitmap == null) {
                    photoProcessing = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_image_open_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    // Обрезка открывается сразу, распознавание идёт параллельно
                    pendingBarcode = null
                    pendingCropBitmap = bitmap
                    croppingSide = side
                    photoProcessing = false
                    scope.launch {
                        pendingBarcode = BarcodeDecoder.decodeBestEffort(context, bitmap)
                    }
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

    fun applyBarcode(barcode: Barcode) {
        val value = barcode.rawValue ?: return
        codeValue = value
        detectedFormat = barcodeFormatName(barcode.format)
    }

    // После обрезки: фото — на своё место (старое заменяется), код — в форму
    fun onCropDone(side: String?, cropped: Bitmap?) {
        val target = side ?: return
        val knownBarcode = pendingBarcode
        pendingBarcode = null
        pendingCropBitmap = null
        croppingSide = null
        if (cropped == null) return
        photoProcessing = true
        scope.launch {
            // Код не нашёлся на полном кадре — пробуем распознать его на обрезанном
            var barcode = knownBarcode
            if (barcode == null) {
                barcode = BarcodeDecoder.decodeBestEffort(context, cropped)
            }
            // Фото с кодом всегда считается оборотом, без кода — лицевой
            val goesToBack = barcode != null || target == "back"
            val path = withContext(Dispatchers.IO) { PhotoStore.save(context, cropped) }
            if (goesToBack) {
                backPhotoPath?.takeIf { it != path }?.let { old ->
                    if (existing == null) PhotoStore.delete(context, old) else orphanPaths.add(old)
                }
                backPhotoPath = path
                barcode?.let { applyBarcode(it) }
                    ?: Toast.makeText(
                        context,
                        context.getString(R.string.toast_barcode_not_found_photo),
                        Toast.LENGTH_LONG,
                    ).show()
            } else {
                frontPhotoPath?.takeIf { it != path }?.let { old ->
                    if (existing == null) PhotoStore.delete(context, old) else orphanPaths.add(old)
                }
                frontPhotoPath = path
            }
            photoProcessing = false
        }
    }

    // Съёмка стороны карты в окне камеры приложения, с подсказкой
    val captureFrontInstruction = stringResource(R.string.capture_instruction_front)
    val captureBackInstruction = stringResource(R.string.capture_instruction_back)
    capturingSide?.let { side ->
        PhotoCaptureScreen(
            instruction = if (side == "front") captureFrontInstruction else captureBackInstruction,
            onCaptured = { bitmap ->
                capturingSide = null
                // Обрезка открывается сразу: пользователь не ждёт распознавания,
                // ML Kit работает параллельно
                pendingBarcode = null
                pendingCropBitmap = bitmap
                croppingSide = side
                scope.launch {
                    pendingBarcode = BarcodeDecoder.decodeBestEffort(context, bitmap)
                }
            },
            onClose = { capturingSide = null },
        )
        return
    }

    // Обрезка снимка (лицевую сторону сразу под пропорцию карты).
    // Если битмап потерян (поворот) — стадию сбрасывает LaunchedEffect выше.
    val cropBitmap = pendingCropBitmap
    if (croppingSide != null && cropBitmap != null) {
        PhotoCropScreen(
            bitmap = cropBitmap,
            lockCardRatio = croppingSide == "front",
            onDone = { cropped -> onCropDone(croppingSide, cropped) },
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
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_code_not_found),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // Выбор источника фото: камера или галерея
    choosingSide?.let { side ->
        AlertDialog(
            onDismissRequest = { choosingSide = null },
            title = { Text(stringResource(R.string.dialog_photo_title)) },
            text = {
                Text(
                    if (side == "front") {
                        stringResource(R.string.dialog_photo_text_front)
                    } else {
                        stringResource(R.string.dialog_photo_text_back)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        choosingSide = null
                        capturingSide = side
                    },
                ) { Text(stringResource(R.string.action_take_photo)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        choosingSide = null
                        useGallery(side)
                    },
                ) { Text(stringResource(R.string.action_from_gallery)) }
            },
        )
    }

    // Подтверждение удаления карты
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_delete_text,
                        existing?.storeName ?: "",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        existing?.let { card ->
                            viewModel.delete(
                                card.copy(
                                    frontPhotoPath = frontPhotoPath,
                                    backPhotoPath = backPhotoPath,
                                ),
                                onDone = {
                                    // Вместе с картой уходят и её отложенные замены
                                    orphanPaths.forEach { PhotoStore.delete(context, it) }
                                    onSaved()
                                },
                            )
                        }
                    },
                ) { Text(stringResource(R.string.action_delete), color = AccentDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
                text = stringResource(if (existing == null) R.string.title_new else R.string.title_edit),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            // Круглые кнопки: удаление (в редактировании), крестик — отмена, галочка — сохранить
            if (existing != null) {
                HeaderIconButton(
                    onClick = { confirmDelete = true },
                    filled = false,
                    content = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.cd_delete_card),
                            tint = AccentDanger,
                        )
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            HeaderIconButton(
                onClick = { cancel() },
                filled = false,
                content = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
            Spacer(Modifier.width(8.dp))
            HeaderIconButton(
                onClick = { saveCard() },
                filled = true,
                enabled = canSave,
                content = {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.cd_save),
                        tint = if (canSave) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        }

        OutlinedTextField(
            value = storeName,
            onValueChange = { storeName = it },
            label = { Text(stringResource(R.string.field_store_name)) },
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
            text = stringResource(R.string.section_photos),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoSlot(
                label = stringResource(R.string.slot_front_label),
                hint = stringResource(R.string.slot_front_hint),
                photoPath = frontPhotoPath,
                onShoot = { choosingSide = "front" },
                onClear = {
                    // В редактировании файл ещё принадлежит сохранённой карте:
                    // убираем из формы, реальное удаление — после сохранения
                    if (existing == null) {
                        PhotoStore.delete(context, frontPhotoPath)
                    } else {
                        frontPhotoPath?.let { orphanPaths.add(it) }
                    }
                    frontPhotoPath = null
                },
                modifier = Modifier.weight(1f),
            )
            PhotoSlot(
                label = stringResource(R.string.slot_back_label),
                hint = stringResource(R.string.slot_back_hint),
                photoPath = backPhotoPath,
                onShoot = { choosingSide = "back" },
                onClear = {
                    if (existing == null) {
                        PhotoStore.delete(context, backPhotoPath)
                    } else {
                        backPhotoPath?.let { orphanPaths.add(it) }
                    }
                    backPhotoPath = null
                },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.photos_autodetect_hint),
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
            label = { Text(stringResource(R.string.field_code_value)) },
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
                        contentDescription = stringResource(R.string.cd_code_preview),
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
                    val detected = detectedFormat
                    if (detected != null) {
                        Text(
                            text = stringResource(R.string.code_detected, detected),
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
            Text(stringResource(R.string.btn_code_from_file))
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
            // Декод JPEG — асинхронно, вне главного потока
            val photo = rememberPhoto(photoPath, maxDim = 512)
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
                        contentDescription = stringResource(R.string.cd_remove_photo),
                        tint = OnPhotoBadge,
                    )
                }
                // Подсказка про повторный тап
                Text(
                    text = stringResource(R.string.slot_replace_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnPhotoBadge,
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
