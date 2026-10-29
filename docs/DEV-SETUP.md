# Настройка окружения (для нового пользователя / новой машины)

Проект — Android-приложение «Мои карты» (Cardholder, Kotlin + Jetpack Compose).
План этапов — [ROADMAP.md](ROADMAP.md). Текущее состояние см. в GitHub Releases.

## 1. Инструменты — уже есть, устанавливать НЕ нужно

Всё лежит в `E:\AI\ZCode\tools` (portable-версии):

| Инструмент | Путь |
|---|---|
| Git 2.56 | `E:\AI\ZCode\tools\git\cmd\git.exe` |
| GitHub CLI 2.103 | `E:\AI\ZCode\tools\bin\gh.exe` |
| JDK 17 | `E:\AI\ZCode\tools\jdk17` |
| Android SDK (API 34) | `E:\AI\ZCode\tools\android-sdk` |
| Gradle 8.7 | `E:\AI\ZCode\tools\gradle-8.7` (+ wrapper в проекте) |
| Node.js 22 | `E:\AI\ZCode\tools\node` |

Переменные окружения пользователя (добавить, если их нет):
`JAVA_HOME=E:\AI\ZCode\tools\jdk17`, `ANDROID_HOME=E:\AI\ZCode\tools\android-sdk`, `GRADLE_USER_HOME=E:\AI\ZCode\tools\gradle-home`,
и добавить в `Path`: `E:\AI\ZCode\tools\git\cmd;E:\AI\ZCode\tools\bin;E:\AI\ZCode\tools\node`.

## 2. Получить проект

Проект живёт в `E:\AI\ZCode\cardholder`.

```bat
cd E:\AI\ZCode
git clone https://github.com/tashik54-tatar/cardholder.git
cd cardholder
```

Создать `local.properties` в корне проекта (одна строка):
```
sdk.dir=E:/AI/ZCode/tools/android-sdk
```

## 3. Git-идентичность (на нового пользователя)

```bat
git config --global user.name "tashik54-tatar"
git config --global user.email "348998645+tashik54-tatar@users.noreply.github.com"
git config --global init.defaultBranch main
```

## 4. Вход в GitHub

Ключи хранятся в профиле пользователя, поэтому на новой учётной записи нужен свой вход:

```bat
gh auth login --hostname github.com --git-protocol https --web
```

(откроется браузер с одноразовым кодом), затем:

```bat
gh auth setup-git
```

## 5. Сборка APK

```bat
gradlew.bat assembleDebug
```

APK: `app\build\outputs\apk\debug\MyCards-v<версия>-debug.apk` (версия в имени).

Релиз каждого этапа: коммит → тег `vX.Y` → push → `gh release create vX.Y <apk>`.

## Примечания

- Версии в проекте: AGP 8.5.2, Kotlin 2.0.20, KSP, Room 2.6.1, CameraX 1.3.4, ML Kit barcode 17.3.0, ZXing core 3.5.3, minSdk 26 / target 34.
- Тест запуска: `gradlew.bat testDebugUnitTest` (Robolectric).
- Кэш Gradle общий: `E:\AI\ZCode\tools\gradle-home`.
- Настройки скиллов агента: `C:\Users\TashiK\.agents\skills` (скиллы Android/Compose/CameraX установлены через `npx skills`).
