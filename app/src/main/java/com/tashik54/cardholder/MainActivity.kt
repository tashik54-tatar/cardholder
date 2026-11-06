package com.tashik54.cardholder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tashik54.cardholder.data.AppSettings
import com.tashik54.cardholder.data.Card
import com.tashik54.cardholder.data.CardRepository
import com.tashik54.cardholder.data.PhotoStore
import com.tashik54.cardholder.ui.CardListViewModel
import com.tashik54.cardholder.ui.screens.AddCardScreen
import com.tashik54.cardholder.ui.screens.CardScreen
import com.tashik54.cardholder.ui.screens.HomeScreen
import com.tashik54.cardholder.ui.screens.SettingsScreen
import com.tashik54.cardholder.ui.theme.CardHolderTheme
import com.tashik54.cardholder.ui.theme.GradientBackground

private sealed interface Screen {
    data object Home : Screen
    data object Add : Screen
    data object Settings : Screen
    data class Detail(val cardId: Long) : Screen
    data class Edit(val cardId: Long) : Screen
}

/**
 * Навигация сохраняется через rememberSaveable (тип + cardId в Bundle),
 * поэтому поворот/сворачивание/смерть процесса не сбрасывают приложение
 * на главный экран. Edit хранит id, а не снимок Card: форма всегда
 * редактирует живые данные из БД.
 *
 * Значения — строки, а не числа. На части устройств (наблюдалось на
 * Android 15, TECNO LI6) системная перезапаковка SavedState при повороте
 * возвращает числа из сохранённых списков другим типом (Integer приходит
 * как Long), и жёсткий каст `as Int` падает с ClassCastException —
 * приложение «закрывалось» на повороте с любого экрана. Строки этот
 * путь искажать не умеет; malformed-состояние честно деградирует в Home.
 */
private val ScreenSaver: Saver<Screen, Any> = listSaver(
    save = { s: Screen ->
        when (s) {
            Screen.Home -> listOf("home")
            Screen.Add -> listOf("add")
            Screen.Settings -> listOf("settings")
            is Screen.Detail -> listOf("detail", s.cardId.toString())
            is Screen.Edit -> listOf("edit", s.cardId.toString())
        }
    },
    restore = { values: List<Any> ->
        when (values[0] as? String) {
            "add" -> Screen.Add
            "settings" -> Screen.Settings
            "detail" -> (values.getOrNull(1) as? String)
                ?.toLongOrNull()?.let(Screen::Detail) ?: Screen.Home
            "edit" -> (values.getOrNull(1) as? String)
                ?.toLongOrNull()?.let(Screen::Edit) ?: Screen.Home
            else -> Screen.Home
        }
    },
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.init(this)
        // Временный каталог снимков камеры чистится при старте:
        // файлы, осиротевшие при смерти процесса, не копятся.
        PhotoStore.cleanupCaptures(this)
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
    var screen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf<Screen>(Screen.Home)
    }

    // Системный «назад» возвращает на главный экран, а не закрывает приложение.
    // Экраны со своими состояниями (карта, форма) ставят свои BackHandler —
    // они имеют приоритет над этим.
    BackHandler(enabled = screen != Screen.Home) {
        screen = Screen.Home
    }

    val context = LocalContext.current
    val repository = remember(context) { CardRepository.from(context) }
    val viewModel: CardListViewModel =
        viewModel(factory = CardListViewModel.factory(repository))
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    // На странице карты и в форме редактирования нижнее меню не показываем:
    // там свои действия («назад», «редактировать», «удалить»), и ни один
    // пункт меню не должен выглядеть выбранным.
    val showBottomBar = screen !is Screen.Detail && screen !is Screen.Edit

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                BottomBar(screen, onSelect = { screen = it })
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(
                    cards = cards,
                    modifier = Modifier.padding(padding),
                    onCardClick = { screen = Screen.Detail(it.id) },
                )
                Screen.Add -> AddCardScreen(
                    modifier = Modifier.padding(padding),
                    onSaved = { screen = Screen.Home },
                    onCancel = { screen = Screen.Home },
                )
                Screen.Settings -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    onBack = { screen = Screen.Home },
                )
                is Screen.Detail -> CardScreen(
                    cardId = s.cardId,
                    modifier = Modifier.padding(padding),
                    onBack = { screen = Screen.Home },
                    onEdit = { card -> screen = Screen.Edit(card.id) },
                )
                is Screen.Edit -> {
                    // Карту берём из живого потока БД (не снимок из списка):
                    // после поворота/смерти процесса форма получает актуальные
                    // данные, а удалённая карта не «воскрешается».
                    val editing = cards.firstOrNull { it.id == s.cardId }
                    if (editing == null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AddCardScreen(
                            existing = editing,
                            modifier = Modifier.padding(padding),
                            onSaved = { screen = Screen.Home },
                            onCancel = { screen = Screen.Home },
                        )
                    }
                }
            }
        }
    }
}

/** Нижнее меню: «+» (добавить карту) и «⚙» (настройки). */
@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val addLabel = stringResource(R.string.nav_add)
    val settingsLabel = stringResource(R.string.nav_settings)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
        BottomItem(Icons.Filled.Add, addLabel, current is Screen.Add) { onSelect(Screen.Add) }
        BottomItem(Icons.Filled.Settings, settingsLabel, current is Screen.Settings) { onSelect(Screen.Settings) }
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
