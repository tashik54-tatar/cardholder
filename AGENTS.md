# AGENTS.md — cardholder («Мои карты»)

Прочитай этот файл перед работой, если ты новый чат/новая учётка Windows: он восстанавливает
историю и карту проекта. Исходники — на GitHub, воссоздавать ничего не нужно, здесь описано
что где и зачем.

## Что это
Android-приложение «Мои карты» — офлайн-кошелёк скидочных/бонусных карт: пользователь
добавляет карту (фото стороны со штрихкодом из камеры или галереи, можно скриншот) — ML Kit
распознаёт код; лицевая сторона обрезается в формате банковской карты (1.586); на кассе тап
по карте показывает штрихкод/QR на весь экран на белом. Плюс фото сторон, полное
редактирование, CSV-бэкап, темы. Стек: Kotlin, Jetpack Compose (M3), Room, CameraX, ML Kit
(офлайн), ZXing, minSdk 26 / target 34, AGP 8.5.2 / Kotlin 2.0.20.
Личный проект Дена.

- Папка: `E:\AI\ZCode\проекты\cardholder`
- Репо (private): `github.com/tashik54-tatar/cardholder`
- Приватные данные (ключи подписи и пр.): `E:\AI\ZCode\Записки Дену\пароли-и-ключи.md`

## Окружение (портативное, без студии)
- git: `E:\AI\ZCode\tools\git\cmd\git.exe` (в Path: `E:\AI\ZCode\tools\git\cmd`)
- gh: `E:\AI\ZCode\tools\bin\gh.exe` (акк tashik54-tatar, авторизован на учётках TashiK и Work)
- JDK17: `E:\AI\ZCode\tools\jdk17`; android-sdk: `E:\AI\ZCode\tools\android-sdk`;
  gradle-home: `E:\AI\ZCode\tools\gradle-home`; node: `E:\AI\ZCode\tools\node`
- local.properties → `sdk.dir=E:/AI/ZCode/tools/android-sdk`
- Подробно: `docs/DEV-SETUP.md`; состояние дел: `E:\AI\ZCode\journal.md` (раздел Cardholder)

## История версий (что сделано)

- **v0.1** — репозиторий, README, роадмап, .gitignore
- **v0.2** — скелет: Compose, нижнее меню («+», «⚙»), навигация, шрифт Inter, градиентный фон
- **v0.3** — БД Room: Card (название, тип кода, значение, пути к фото сторон)
- **v0.4** — форма добавления карты (ручной ввод значения)
- **v0.5** — живой сканер CameraX + ML Kit (позже убран: код распознаётся из фото)
- **v0.6** — рендер кода из значения (ZXing), экран карты по тапу, код крупно на белом
- **v0.7** (0.7–0.7.6) — фото сторон: съёмка, ручная обрезка, фото на главной; переименование
  «Кархолдер» → «Мои карты»; автотип кода; предпросмотр кода в форме; своя камера с
  подсказкой (PhotoCaptureScreen); версия в имени APK
- **v0.8** — настройки: импорт/экспорт CSV (CsvCodec, RFC 4180, «;», BOM, автодетект)
- **v0.9** (0.9–0.9.6) — полировка: узор фона в стиле Telegram, тёмная тема, анимации;
  полноэкранные Dialog-окна (фикс перехвата касаний меню); системный «назад» → главное меню;
  галочка/крестик в шапке формы; фото из камеры ИЛИ галереи; фото на странице карты за
  кнопкой (настройка); выбор темы авто/светлая/тёмная; адаптивная иконка; полное
  редактирование той же формой; фикс подмены QR→штрихкод при редактировании
- **v1.0** (04.11) — иконка «Тёмная люкс» (выбрана Деном из 6 концепций,
  сгенерированных workflow-комбайном), release-APK с R8 (25 МБ), подписан debug-ключом,
  README с лого. https://github.com/tashik54-tatar/cardholder/releases/tag/v1.0
- **v1.0.2** (07.11, ТЕКУЩИЙ РЕЛИЗ) — фикс краша на повороте экрана: на Android 15
  (TECNO LI6) системная перезапаковка SavedState возвращает Integer из сохранённых
  списков как Long, жёсткий каст в ScreenSaver падал. ScreenSaver переведён на
  строки. Детали — FIX-REPORT.md.
  https://github.com/tashik54-tatar/cardholder/releases/tag/v1.0.2

## Карта файлов

```
app/src/main/java/com/tashik54/cardholder/
  MainActivity.kt            — навигация (sealed Screen), BackHandler, AppSettings.init
  data/                      — Card, CardDao, CardHolderDatabase, CardRepository,
                               CodeTypeResolver, CodeRenderer (ZXing), BarcodeDecoder (ML Kit,
                               decodeBestEffort), PhotoStore (filesDir/photos, EXIF), CsvCodec,
                               AppSettings (SharedPreferences + Compose-состояние)
  ui/                        — AddCardViewModel (insert/update/delete), CardDetailViewModel,
                               CardListViewModel, scan/PhotoCaptureScreen (камера-Dialog)
  ui/screens/                — HomeScreen (сетка, Fit/FillBounds), AddCardScreen (форма
                               «новая/редактирование», выбор источника фото, предпросмотр),
                               CardScreen (код крупно, полноэкранный код, фото за кнопкой),
                               SettingsScreen (CSV, скрыть фото, тема), PhotoCropScreen
                               (рамка, lockCardRatio 1.586)
  ui/theme/Theme.kt          — Inter/InterDisplay, светлые/тёмные схемы, градиент, PatternOverlay
app/src/main/res/drawable/ic_launcher_*.xml — иконка «Тёмная люкс» (вектор, адаптивная)
design/logos/                — 6 SVG-концепций лого + index.html-галерея
docs/ROADMAP.md, docs/DEV-SETUP.md
app/src/test/                — 10 Robolectric/JUnit-тестов (запуск Activity, тип кода, CSV)
```

## Сборка и релизы
- `gradlew.bat assembleDebug` / `assembleRelease`; APK: `app/build/outputs/apk/*/MyCards-v<ver>-*.apk`
- Релиз: коммит → тег `vX.Y[.Z]` → push → `gh release create vX.Y.Z <apk>` (+release, +debug)
- versionCode/versionName: `app/build.gradle.kts` (v1.0.2 = code 25)
- Release подписан debug-keystore'ом учётки TashiK (детали — пароли-и-ключи): сборка с другой
  учётки не встанет поверх обновлением
- Перед пушем вычищать секреты/токены (.kwork-token.json уже ловили — не про этот проект, но правило общее)

## Тонкие места (не сломать)
- Камера, обрезка, полноэкранные фото/код — Dialog-окна: иначе нижнее меню перехватывает касания
- В редактировании, пока значение кода не менялось, тип кода сохраняется исходным (QR не
  «угадывать» заново)
- Пропорция рамки обрезки: 1.586 задана в экранных координатах и пересчитывается в координаты
  фото (иначе на вертикальных фото рамка «поворачивается»)
- Фото-файлы: старые удаляются после успешного сохранения (orphanPaths), при отмене новой
  карты — сразу
- Аудит GLM 5.3: `AUDIT-REPORT.md` в корне репо (+ `cardholder-fixed/` — правки от GLM,
  требуют ревью перед вливанием), рабочая папка аудита `E:\AI\ZCode\audit-cardholder-v1.0`

## Статус и планы
- Текущий релиз: **v1.0.2** (07.11) — фикс краша на повороте (ScreenSaver на строках,
  см. FIX-REPORT.md). До этого: v1.0.1 (05.11) — все 29 находок аудита GLM исправлены
  (v1.1-коммит, нумерация приведена к semver-патчу). Отчёты: `FIX-REPORT.md`,
  `AUDIT-REPORT.md`. Своя подпись: `keystore/release.jks` (+keystore.properties) — в git не
  хранятся (gitignore), файлы существуют только на диске этой учётки; детали — пароли-и-ключи
- Тонкое место железа Дена: TECNO LI6 / Android 15 искажает типы чисел в SavedState при
  повороте (Int приходит как Long) — в кастомных Saver хранить только строки/Boolean,
  числовые значения кодировать строкой; жёсткие касты при восстановлении не делать
- Сборка: через ASCII-джанкшн `E:\AI\ZCode\tools\cardholder-build` (AGP не берёт кириллицу;
  в его gradle.properties добавлен android.overridePathCheck=true). Тесты через джанкшн НЕ
  работают (тест-воркер не видит классы) — копировать исходники в ASCII-путь (например
  %TEMP%) и гонять `testDebugUnitTest` там
- Дальше: Ден пользуется, косяки → v1.0.x; скриншоты в README с его устройства
- Правки аудита делала другая сессия (GLM): при сомнениях смотреть git log и FIX-REPORT.md
