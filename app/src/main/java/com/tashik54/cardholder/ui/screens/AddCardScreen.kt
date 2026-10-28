package com.tashik54.cardholder.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.tashik54.cardholder.data.BarcodeDecoder
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CodeRenderer
import com.tashik54.cardholder.data.PhotoStore
import com.tashik54.cardholder.data.barcodeFormatName
import com.tashik54.cardholder.data.resolveCodeType
import com.tashik54.cardholder.ui.AddCardViewModel
import com.tashik54.cardholder.ui.scan.ScannerScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Форма новой карты: название магазина, значение кода и фото сторон.
 * Тип кода не выбирается вручную: сканер/распознавание из файла
 * определяют формат сами, при ручном вводе тип угадывается по значению.
 * Как только значение введено, код сразу показывается картинкой.
 */
@Composable
fun AddCardScreen(
    modifier: Modifier = Modifier,
    onSaved: () -> Unit,
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

    var scanning by rememberSaveable { mutableStateOf(false) }
    // "front" / "back" — какая сторона сейчас снимается или обрезается
    var capturingSide by rememberSaveable { mutableStateOf<String?>(null) }
    var croppingSide by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureFile by remember { mutableStateOf<File?>(null) }

    val canSave = storeName.isNotBlank() && codeValue.isNotBlank()

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

    fun onPhotoTaken(file: File) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { PhotoStore.loadFromUri(context, uriOf(context, file)) }
            if (bitmap == null) {
                Toast.makeText(context, "Не удалось открыть снимок", Toast.LENGTH_SHORT).show()
            } else {
                pendingCropBitmap = bitmap
                croppingSide = capturingSide
            }
            file.delete()
            capturingSide = null
        }
    }

    if (scanning) {
        ScannerScreen(
            onDetected = { barcode ->
                scanning = false
                applyBarcode(barcode)
            },
            onClose = { scanning = false },
        )
        return
    }

    // Съёмка стороны карты штатной камерой
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
        takePicture.launch(uriOf(context, file))
    }

    // Экран обрезки снимка
    if (croppingSide != null) {
        PhotoCropScreen(
            bitmap = pendingCropBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            onDone = { cropped ->
                val side = croppingSide
                if (cropped != null && side != null) {
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { PhotoStore.save(context, cropped) }
                        if (side == "front") PhotoStore.delete(frontPhotoPath)
                        else PhotoStore.delete(backPhotoPath)
                        if (side == "front") frontPhotoPath = path else backPhotoPath = path
                    }
                }
                pendingCropBitmap = null
                croppingSide = null
            },
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Новая карта",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = storeName,
            onValueChange = { storeName = it },
            label = { Text("Название магазина") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.7f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

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
                focusedContainerColor = Color.White.copy(alpha = 0.7f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Предпросмотр: код картинкой сразу видно, что именно сохранится
        if (previewBitmap != null) {
            Spacer(Modifier.height(12.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Предпросмотр кода",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (resolvedType == com.tashik54.cardholder.data.CodeType.QR) {
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

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Считать код",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text("Из файла")
            }
            OutlinedButton(
                onClick = { scanning = true },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.padding(start = 6.dp))
                Text("Камера")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Фото сторон",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoSlot(
                label = "Лицевая",
                photoPath = frontPhotoPath,
                onShoot = { startCapture("front") },
                onClear = {
                    PhotoStore.delete(frontPhotoPath)
                    frontPhotoPath = null
                },
                modifier = Modifier.weight(1f),
            )
            PhotoSlot(
                label = "Оборотная",
                photoPath = backPhotoPath,
                onShoot = { startCapture("back") },
                onClear = {
                    PhotoStore.delete(backPhotoPath)
                    backPhotoPath = null
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.save(
                    storeName = storeName,
                    codeType = resolvedType,
                    codeValue = codeValue,
                    barcodeFormat = detectedFormat?.takeIf { resolvedType != com.tashik54.cardholder.data.CodeType.QR },
                    frontPhotoPath = frontPhotoPath,
                    backPhotoPath = backPhotoPath,
                    onDone = onSaved,
                )
            },
            enabled = canSave,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                "Сохранить",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun uriOf(context: android.content.Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Слот фото стороны: превью, кнопка съёмки, кнопка удаления. */
@Composable
private fun PhotoSlot(
    label: String,
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
                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        ) {
            val photo = remember(photoPath) { PhotoStore.load(photoPath, maxDim = 512) }
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(onClick = onClear, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Убрать фото",
                        tint = Color(0xFF1C1B22),
                    )
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onShoot, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.padding(start = 6.dp))
            Text(if (photoPath == null) "Снять" else "Заменить")
        }
    }
}
