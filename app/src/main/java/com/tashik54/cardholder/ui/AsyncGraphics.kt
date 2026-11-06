package com.tashik54.cardholder.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.tashik54.cardholder.data.CodeRenderer
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Асинхронная загрузка фото с диска: декод JPEG уходит с главного потока,
 * при смене пути (замена фото) перезагружается автоматически.
 */
@Composable
fun rememberPhoto(path: String?, maxDim: Int = 512): Bitmap? =
    produceState<Bitmap?>(initialValue = null, path, maxDim) {
        this.value = withContext(Dispatchers.IO) { PhotoStore.load(path, maxDim) }
    }.value

/**
 * Асинхронный рендер кода (ZXing): тяжёлый encode уходит с главного
 * потока. Ключи — значение, тип и формат: при изменении любого из них
 * предпросмотр пересчитывается (раньше формат не входил в ключ и
 * предпросмотр мог показать устаревшую картинку).
 * Возвращает null, если значение закодировать нельзя.
 */
@Composable
fun rememberCodeBitmap(
    value: String,
    codeType: CodeType,
    barcodeFormat: String?,
    width: Int,
    height: Int,
): Bitmap? {
    val input = value
    return produceState<Bitmap?>(
        initialValue = null,
        input, codeType, barcodeFormat, width, height,
    ) {
        this.value = if (input.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                CodeRenderer.render(input, codeType, barcodeFormat, width, height)
            }
        }
    }.value
}
