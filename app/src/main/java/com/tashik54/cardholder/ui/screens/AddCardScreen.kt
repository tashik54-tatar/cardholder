package com.tashik54.cardholder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tashik54.cardholder.data.CodeType
import com.tashik54.cardholder.ui.AddCardViewModel
import com.tashik54.cardholder.data.CardRepository

/**
 * Форма новой карты: название магазина, тип кода и его значение.
 * Источник кода — файл (выбор картинки, расшифровка добавится в v0.5
 * вместе с ML Kit) или камера (v0.5). Пока значение вводится вручную.
 */
@Composable
fun AddCardScreen(
    modifier: Modifier = Modifier,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AddCardViewModel =
        viewModel(factory = AddCardViewModel.factory(CardRepository.from(context)))

    var storeName by rememberSaveable { mutableStateOf("") }
    var codeValue by rememberSaveable { mutableStateOf("") }
    var codeType by rememberSaveable { mutableStateOf(CodeType.BARCODE.name) }

    val canSave = storeName.isNotBlank() && codeValue.isNotBlank()

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

        Text(
            text = "Тип кода",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = codeType == CodeType.BARCODE.name,
                onClick = { codeType = CodeType.BARCODE.name },
                label = { Text("Штрихкод") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
            )
            FilterChip(
                selected = codeType == CodeType.QR.name,
                onClick = { codeType = CodeType.QR.name },
                label = { Text("QR-код") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = codeValue,
            onValueChange = { codeValue = it },
            label = { Text("Значение кода (номер карты или ссылка)") },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.7f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Считать код",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { /* выбор файла заработает в v0.5 вместе с расшифровкой */ },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Spacer(Modifier.padding(start = 6.dp))
                Text("Из файла")
            }
            OutlinedButton(
                onClick = { /* сканер камеры появится в v0.5 */ },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.padding(start = 6.dp))
                Text("Камера — скоро")
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                viewModel.save(
                    storeName = storeName,
                    codeType = CodeType.valueOf(codeType),
                    codeValue = codeValue,
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
            Text("Сохранить", style = TextStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize))
        }
        Spacer(Modifier.height(16.dp))
    }
}
