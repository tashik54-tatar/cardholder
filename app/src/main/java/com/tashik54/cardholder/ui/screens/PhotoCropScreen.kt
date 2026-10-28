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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

/**
 * Ручная обрезка фото: перетаскиваемая прямоугольная рамка с угловыми
 * маркерами; рамку можно таскать за внутреннюю область.
 */
@Composable
fun PhotoCropScreen(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    onDone: (Bitmap?) -> Unit,
) {
    // Рамка в нормированных координатах (0..1 относительно показа фото)
    var rect by remember { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }
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
            text = "Обрежьте фото",
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
                contentDescription = "Фото для обрезки",
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

                CropOverlay(
                    rect = rect,
                    areaOffset = shown,
                    areaSize = Size(shownW, shownH),
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
            ) { Text("Отмена") }
            Button(
                onClick = {
                    onDone(cropBitmap(bitmap, rect))
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
            ) { Text("Готово") }
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
