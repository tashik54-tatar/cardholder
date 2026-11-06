package com.tashik54.cardholder.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tashik54.cardholder.R
import kotlin.math.abs

/**
 * Ручная обрезка фото: перетаскиваемая прямоугольная рамка с угловыми
 * маркерами; рамку можно таскать за внутреннюю область.
 *
 * @param lockCardRatio если true — рамка зафиксирована в пропорции
 * банковской карты (1.586): размер меняется, соотношение сторон нет.
 */
@Composable
fun PhotoCropScreen(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    lockCardRatio: Boolean = false,
    onDone: (Bitmap?) -> Unit,
) {
    val cardRatio = 1.586f
    // Рамка в нормированных координатах (0..1 относительно показа фото).
    // Пропорция карты задана в ЭКРАННЫХ координатах; в координатах фото она
    // другая (зависит от пропорций самого фото) — корректируется после
    // первого измерения shown-области (см. ниже).
    var rect by remember { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }
    var rectInitialized by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Полноэкранное окно: нижнее меню приложения не перехватывает касания
    Dialog(
        onDismissRequest = { onDone(null) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(R.string.crop_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_photo_for_crop),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it },
                )
                if (canvasSize != IntSize.Zero) {
                    // Пропорции показанного фото (ContentScale.Fit вписывает его в Box)
                    val imgW = canvasSize.width.toFloat()
                    val imgH = canvasSize.height.toFloat()
                    val bmpAspect = bitmap.width.toFloat() / bitmap.height
                    val boxAspect = imgW / imgH
                    val shownW: Float
                    val shownH: Float
                    if (bmpAspect > boxAspect) {
                        shownW = imgW
                        shownH = imgW / bmpAspect
                    } else {
                        shownH = imgH
                        shownW = imgH * bmpAspect
                    }
                    val left = (imgW - shownW) / 2f
                    val top = (imgH - shownH) / 2f
                    val shown = Offset(left, top)

                    // Стартовая рамка под формат карты: пересчёт пропорции
                    // из экранных координат в координаты фото.
                    if (lockCardRatio && !rectInitialized && shownW > 0f && shownH > 0f) {
                        // w/h в координатах фото, дающий 1.586 на экране
                        val normRatio = cardRatio * shownH / shownW
                        var w = 0.9f
                        var h = w / normRatio
                        if (h > 0.95f) { h = 0.95f; w = h * normRatio }
                        rect = Rect(0.5f - w / 2, 0.5f - h / 2, 0.5f + w / 2, 0.5f + h / 2)
                        rectInitialized = true
                    }

                    CropOverlay(
                        rect = rect,
                        areaOffset = shown,
                        areaSize = Size(shownW, shownH),
                        aspectRatio = if (lockCardRatio) cardRatio else null,
                        onRectChange = { rect = it },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onDone(null) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = {
                        onDone(cropBitmap(bitmap, rect))
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_done)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun cropBitmap(src: Bitmap, rect: Rect): Bitmap {
    val x = (rect.left * src.width).toInt().coerceIn(0, src.width - 1)
    val y = (rect.top * src.height).toInt().coerceIn(0, src.height - 1)
    val w = (rect.width * src.width).toInt().coerceIn(1, src.width - x)
    val h = (rect.height * src.height).toInt().coerceIn(1, src.height - y)
    return Bitmap.createBitmap(src, x, y, w, h)
}

@Composable
private fun CropOverlay(
    rect: Rect,
    areaOffset: Offset,
    areaSize: Size,
    aspectRatio: Float?,
    onRectChange: (Rect) -> Unit,
) {
    val handleRadius = 14.dp.value

    var dragTarget by remember { mutableStateOf<String?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    // pointerInput(Unit) создаёт замыкание один раз — без rememberUpdatedState
    // оно держало бы устаревший rect, и рамка «не тянулась»
    val currentRect by rememberUpdatedState(rect)
    val currentAreaOffset by rememberUpdatedState(areaOffset)
    val currentAreaSize by rememberUpdatedState(areaSize)
    val currentOnRectChange by rememberUpdatedState(onRectChange)

    fun toNormLive(pos: Offset): Offset =
        Offset(
            ((pos.x - currentAreaOffset.x) / currentAreaSize.width).coerceIn(0f, 1f),
            ((pos.y - currentAreaOffset.y) / currentAreaSize.height).coerceIn(0f, 1f),
        )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val rect = currentRect
                        val n = toNormLive(pos)
                        val corners = listOf("tl" to Offset(rect.left, rect.top),
                            "tr" to Offset(rect.right, rect.top),
                            "bl" to Offset(rect.left, rect.bottom),
                            "br" to Offset(rect.right, rect.bottom))
                        dragTarget = corners
                            .filter { (_, c) -> abs(c.x - n.x) < 0.09f && abs(c.y - n.y) < 0.09f }
                            .minByOrNull { (_, c) -> (c - n).getDistance() }?.first
                            ?: if (n.x in rect.left..rect.right && n.y in rect.top..rect.bottom) "move" else null
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val r = currentRect
                        val d = Offset(drag.x / currentAreaSize.width, drag.y / currentAreaSize.height)
                        val min = 0.15f
                        if (aspectRatio != null && dragTarget in listOf("tl", "tr", "bl", "br")) {
                            // Фиксированная пропорция: размер меняется, соотношение — нет.
                            // Пропорция задана в экранных координатах, переводим её
                            // в координаты фото через реальные размеры показанной области.
                            val ratio = aspectRatio * currentAreaSize.height / currentAreaSize.width
                            when (dragTarget) {
                                "br" -> {
                                    var w = (r.right + d.x).coerceIn(r.left + min, 1f) - r.left
                                    var h = w / ratio
                                    if (r.top + h > 1f) { h = 1f - r.top; w = h * ratio }
                                    currentOnRectChange(Rect(r.left, r.top, r.left + w, r.top + h))
                                }
                                "bl" -> {
                                    var w = r.right - (r.left + d.x).coerceIn(0f, r.right - min)
                                    var h = w / ratio
                                    if (r.top + h > 1f) { h = 1f - r.top; w = h * ratio }
                                    currentOnRectChange(Rect(r.right - w, r.top, r.right, r.top + h))
                                }
                                "tr" -> {
                                    var w = (r.right + d.x).coerceIn(r.left + min, 1f) - r.left
                                    var h = w / ratio
                                    if (r.bottom - h < 0f) { h = r.bottom; w = h * ratio }
                                    currentOnRectChange(Rect(r.left, r.bottom - h, r.left + w, r.bottom))
                                }
                                else /* tl */ -> {
                                    var w = r.right - (r.left + d.x).coerceIn(0f, r.right - min)
                                    var h = w / ratio
                                    if (r.bottom - h < 0f) { h = r.bottom; w = h * ratio }
                                    currentOnRectChange(Rect(r.right - w, r.bottom - h, r.right, r.bottom))
                                }
                            }
                        } else {
                            when (dragTarget) {
                                "tl" -> currentOnRectChange(Rect(
                                    (r.left + d.x).coerceIn(0f, r.right - min),
                                    (r.top + d.y).coerceIn(0f, r.bottom - min),
                                    r.right, r.bottom,
                                ))
                                "tr" -> currentOnRectChange(Rect(
                                    r.left,
                                    (r.top + d.y).coerceIn(0f, r.bottom - min),
                                    (r.right + d.x).coerceIn(r.left + min, 1f),
                                    r.bottom,
                                ))
                                "bl" -> currentOnRectChange(Rect(
                                    (r.left + d.x).coerceIn(0f, r.right - min),
                                    r.top,
                                    r.right,
                                    (r.bottom + d.y).coerceIn(r.top + min, 1f),
                                ))
                                "br" -> currentOnRectChange(Rect(
                                    r.left, r.top,
                                    (r.right + d.x).coerceIn(r.left + min, 1f),
                                    (r.bottom + d.y).coerceIn(r.top + min, 1f),
                                ))
                                "move" -> {
                                    val w = r.width
                                    val h = r.height
                                    val nl = (r.left + d.x).coerceIn(0f, 1f - w)
                                    val nt = (r.top + d.y).coerceIn(0f, 1f - h)
                                    currentOnRectChange(Rect(nl, nt, nl + w, nt + h))
                                }
                            }
                        }
                    },
                    onDragEnd = { dragTarget = null },
                )
            },
    ) {
        val absRect = Rect(
            areaOffset.x + rect.left * areaSize.width,
            areaOffset.y + rect.top * areaSize.height,
            areaOffset.x + rect.right * areaSize.width,
            areaOffset.y + rect.bottom * areaSize.height,
        )
        // Затемнение вне рамки
        val dark = Color.Black.copy(alpha = 0.55f)
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRect(absRect)
        }
        clipPath(outside) {
            drawRect(dark, topLeft = Offset.Zero, size = size)
        }
        drawRect(
            Color.White,
            topLeft = absRect.topLeft,
            size = absRect.size,
            style = Stroke(width = 2.dp.toPx()),
        )
        for (c in listOf(
            Offset(absRect.left, absRect.top),
            Offset(absRect.right, absRect.top),
            Offset(absRect.left, absRect.bottom),
            Offset(absRect.right, absRect.bottom),
        )) {
            drawCircle(Color.White, radius = handleRadius, center = c)
            drawCircle(accent, radius = handleRadius * 0.6f, center = c)
        }
    }
}
