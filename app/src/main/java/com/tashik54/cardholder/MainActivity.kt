package com.tashik54.cardholder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.ui.CardListViewModel
import com.tashik54.cardholder.ui.screens.AddCardScreen
import com.tashik54.cardholder.ui.screens.HomeScreen
import com.tashik54.cardholder.ui.screens.SettingsScreen
import com.tashik54.cardholder.ui.theme.CardHolderTheme
import com.tashik54.cardholder.ui.theme.GradientBackground

private sealed interface Screen {
    data object Home : Screen
    data object Add : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardHolderTheme {
                GradientBackground {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    // Обычный remember: тип Screen не кладётся в Bundle, rememberSaveable здесь падал бы
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    val context = LocalContext.current
    val repository = remember(context) { CardRepository.from(context) }
    val viewModel: CardListViewModel =
        viewModel(factory = CardListViewModel.factory(repository))
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { BottomBar(screen, onSelect = { screen = it }) },
    ) { padding ->
        when (screen) {
            Screen.Home -> HomeScreen(cards = cards, modifier = Modifier.padding(padding))
            Screen.Add -> AddCardScreen(Modifier.padding(padding))
            Screen.Settings -> SettingsScreen(Modifier.padding(padding))
        }
    }
}

/** Нижнее меню: «+» (добавить карту) и «⚙» (настройки). */
@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = Color.White.copy(alpha = 0.6f)) {
        BottomItem(Icons.Filled.Add, "Добавить", current is Screen.Add) { onSelect(Screen.Add) }
        BottomItem(Icons.Filled.Settings, "Настройки", current is Screen.Settings) { onSelect(Screen.Settings) }
    }
}

@Composable
private fun RowScope.BottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        ),
    )
}
