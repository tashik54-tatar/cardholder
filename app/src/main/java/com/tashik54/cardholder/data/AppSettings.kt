package com.tashik54.cardholder.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Настройки приложения. Живут в SharedPreferences, в UI отдаются
 * как Compose-состояние — экраны перерисовываются сразу после смены.
 */
object AppSettings {

    private const val PREFS = "app_settings"
    private const val KEY_HIDE_PHOTOS = "hide_photos_on_card_page"
    private const val KEY_THEME = "theme_mode"

    /** Режим темы: THEME_AUTO / THEME_LIGHT / THEME_DARK. */
    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private var prefs: android.content.SharedPreferences? = null

    /** Скрывать фото сторон за кнопкой на странице карты. */
    val hidePhotosOnCardPage = mutableStateOf(false)

    /** Выбранная тема (одна из THEME_*). */
    val themeMode = mutableStateOf(THEME_AUTO)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        hidePhotosOnCardPage.value = p.getBoolean(KEY_HIDE_PHOTOS, false)
        themeMode.value = p.getString(KEY_THEME, THEME_AUTO) ?: THEME_AUTO
    }

    fun setHidePhotosOnCardPage(value: Boolean) {
        hidePhotosOnCardPage.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE_PHOTOS, value)?.apply()
    }

    fun setThemeMode(value: String) {
        themeMode.value = value
        prefs?.edit()?.putString(KEY_THEME, value)?.apply()
    }
}
