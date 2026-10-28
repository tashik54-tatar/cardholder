package com.tashik54.cardholder.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tashik54.cardholder.data.AppSettings
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.CsvCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Настройки: экспорт карт в CSV-файл (резервная копия) и импорт обратно.
 * Фото сторон в CSV не переносятся — только данные карты.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CardRepository.from(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val cards = repository.observeAll().first()
            val csv = CsvCodec.export(cards)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (ok) "Экспортировано карт: ${cards.size}" else "Не удалось сохранить файл",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = importCsv(context, uri, repository)
            val message = buildString {
                append("Импортировано карт: ${result.imported}")
                if (result.skippedRows > 0) append(", пропущено строк: ${result.skippedRows}")
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )

        SettingsRow(icon = Icons.Filled.KeyboardArrowDown, title = "Импорт CSV") {
            importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
        }
        SettingsRow(icon = Icons.Filled.KeyboardArrowUp, title = "Экспорт CSV") {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            exportLauncher.launch("my-cards-$date.csv")
        }

        Spacer(Modifier.padding(top = 8.dp))

        // Скрывать фото сторон за кнопкой на странице карты
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    AppSettings.setHidePhotosOnCardPage(!AppSettings.hidePhotosOnCardPage.value)
                }
                .padding(vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Скрывать фото на странице карты",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = AppSettings.hidePhotosOnCardPage.value,
                onCheckedChange = { AppSettings.setHidePhotosOnCardPage(it) },
            )
        }

        // Тема: авто / светлая / тёмная
        var themeDialog by remember { mutableStateOf(false) }
        SettingsRow(
            icon = Icons.Filled.DarkMode,
            title = "Тема: " + when (AppSettings.themeMode.value) {
                AppSettings.THEME_LIGHT -> "светлая"
                AppSettings.THEME_DARK -> "тёмная"
                else -> "авто (как в системе)"
            },
        ) { themeDialog = true }
        if (themeDialog) {
            AlertDialog(
                onDismissRequest = { themeDialog = false },
                title = { Text("Тема") },
                text = {
                    Column {
                        ThemeOption("Авто (как в системе)", AppSettings.THEME_AUTO) { themeDialog = false }
                        ThemeOption("Светлая", AppSettings.THEME_LIGHT) { themeDialog = false }
                        ThemeOption("Тёмная", AppSettings.THEME_DARK) { themeDialog = false }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { themeDialog = false }) { Text("Отмена") }
                },
            )
        }

        Spacer(Modifier.padding(top = 16.dp))
        Text(
            text = "CSV переносит данные карт (название, тип и значение кода). " +
                "Фото сторон остаются только в приложении.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ImportSummary(val imported: Int, val skippedRows: Int)

private suspend fun importCsv(
    context: android.content.Context,
    uri: Uri,
    repository: CardRepository,
): ImportSummary = withContext(Dispatchers.IO) {
    val text = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull() ?: return@withContext ImportSummary(0, 0)

    val parsed = CsvCodec.parse(text)
    for (card in parsed.cards) {
        repository.add(card)
    }
    ImportSummary(parsed.cards.size, parsed.skippedRows)
}

@Composable
private fun ThemeOption(label: String, mode: String, onSelected: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                AppSettings.setThemeMode(mode)
                onSelected()
            }
            .padding(vertical = 10.dp),
    ) {
        RadioButton(
            selected = AppSettings.themeMode.value == mode,
            onClick = {
                AppSettings.setThemeMode(mode)
                onSelected()
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
