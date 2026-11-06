package com.tashik54.cardholder.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tashik54.cardholder.R
import com.tashik54.cardholder.data.PhotoStore
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Сколько ждём колбэк камеры, прежде чем разблокировать затвор (сбой драйвера). */
private const val CAPTURE_TIMEOUT_MS = 10_000L

/**
 * Съёмка фото стороны карты внутри приложения: живое превью с подсказкой
 * («что сфотографировать»), кнопка затвора. Окно камеры не закрывается
 * при съёмке; готовый снимок возвращается битмапом.
 */
@Composable
fun PhotoCaptureScreen(
    instruction: String,
    modifier: Modifier = Modifier,
    onCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            // Максимальное качество: штрихкод должен читаться со снимка
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    var busy by remember { mutableStateOf(false) }
    var timeoutJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Привязка камеры к lifecycle Activity; ссылка на провайдер хранится,
    // чтобы гарантированно отвязать камеру при закрытии экрана — иначе
    // сенсор и превью продолжали жить до выхода из приложения.
    val boundProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Флаг «композиция жива»: колбэк future может прийти уже после ухода
    val active = remember { AtomicBoolean(true) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            boundProvider.value?.unbindAll()
            return@LaunchedEffect
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (!active.get()) return@addListener
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            boundProvider.value = provider
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            runCatching { boundProvider.value?.unbindAll() }
            timeoutJob?.cancel()
        }
    }

    fun shoot() {
        if (busy) return
        busy = true
        val file = PhotoStore.newCaptureFile(context)
        // Страховка от зависшего колбэка камеры: без таймаута кнопка
        // затвора оставалась бы мёртвой навсегда.
        timeoutJob = scope.launch {
            delay(CAPTURE_TIMEOUT_MS)
            if (busy) {
                busy = false
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_camera_timeout),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    timeoutJob?.cancel()
                    scope.launch {
                        val bitmap = withContext(Dispatchers.IO) { PhotoStore.loadCapture(file) }
                        file.delete()
                        if (bitmap != null) {
                            onCaptured(bitmap)
                        } else {
                            busy = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_capture_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    timeoutJob?.cancel()
                    file.delete()
                    busy = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_camera_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    // Полноэкранное окно поверх всего интерфейса: нижнее меню приложения
    // не перекрывает камеру и не перехватывает касания
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (hasPermission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    text = stringResource(R.string.camera_permission_needed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }

            // Подсказка: что фотографируем
            Text(
                text = instruction,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(top = 56.dp, bottom = 16.dp, start = 56.dp, end = 56.dp),
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_cancel),
                    tint = Color.White,
                )
            }

            // Кнопка затвора
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .size(76.dp)
                    .background(Color.White, CircleShape)
                    .clickable(enabled = !busy && hasPermission) { shoot() },
            ) {
                Box(
                    Modifier
                        .size(62.dp)
                        .background(
                            if (busy) Color.LightGray else Color.Black.copy(alpha = 0.85f),
                            CircleShape,
                        ),
                )
            }
        }
    }
}
