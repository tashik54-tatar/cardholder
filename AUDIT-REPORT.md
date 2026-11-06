# Аудит «Мои карты» (Cardholder) v1.0

Дата аудита: 2026-11-04
Объект: `E:\AI\ZCode\audit-cardholder-v1.0` (исходники — источник правды; APK `MyCards-v1.0-release.apk` — справка по релизу).
Проверено: все 20 Kotlin-файлов `app\src\main\java` + 3 теста, `AndroidManifest.xml`, оба `build.gradle.kts`, `libs.versions.toml`, `proguard-rules.pro`, `gradle.properties`, ресурсы (`strings/colors/themes`), бинарный манифест и блок подписи APK.
Метод: статический анализ, код не изменялся, ничего не собиралось и не запускалось.

---

## 1. Резюме

Общее состояние — **среднее**. Ядро (Room, репозиторий, CSV-кодек, определение типа кода) написано аккуратно и покрыто юнит-тестами; SQL-инъекций нет (все запросы Room — параметризованные), exported-компонентов, кроме лаунчера, нет, поверхностей WebView/Intent почти нет. Однако релизная сборка и жизненный цикл UI содержат серьёзные дефекты.

Главные проблемы:

1. **[C1] Падение приложения при открытии карты**, если значение кода содержит не-ASCII символы (кириллица) или длиннее 80 симв.: `CodeRenderer.render` вызывается без `try` в `CardScreen` — ZXing бросает исключение. Ввод вручную и импорт CSV оба приводят к такому значению. Карту можно сохранить, но нельзя открыть.
2. **[C2] Релиз подписан debug-ключом** (`signingConfig = signingConfigs.getByName("debug")` в release) — подтверждено сертификатом «Android Debug» в APK.
3. **[H1] Навигация не переживает поворот экрана** — состояние экрана в `remember` без сохранения: поворот/сворачивание сбрасывает на главный и теряет заполненную форму.
4. **[H2] Безвозвратная потеря фото карты**: крестик «убрать фото» в редактировании удаляет файл с диска сразу, а «Отмена» не откатывает — в БД остаётся битая ссылка.
5. **[H3] Утечка камеры**: после закрытия экрана съёмки привязка CameraX к lifecycle Activity не освобождается.

---

## 2. Таблица находок

| № | Серьёзность | Категория | Файл:строка | Суть | Что подтвердилось (цитата) |
|---|---|---|---|---|---|
| 1 | critical | баг | `ui/screens/CardScreen.kt:126` | Краш при открытии карты с не-ASCII/длинным (>80) значением кода | `CodeRenderer.render(current.codeValue, ...)` без try-catch; финальный fallback тоже не защищён: `CodeRenderer.kt:37` `?: MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, width, height)` |
| 2 | critical | уязвимость | `app/build.gradle.kts:36` | Release APK подписан debug-ключом | `signingConfig = signingConfigs.getByName("debug")` внутри `buildTypes.release`; в подписи APK найден сертификат «Android Debug» |
| 3 | high | баг | `MainActivity.kt:73` | Навигация теряется при повороте/пересоздании Activity | `var screen by remember { mutableStateOf<Screen>(Screen.Home) }` — не saveable |
| 4 | high | логика | `ui/screens/AddCardScreen.kt:485` | Крестик фото в редактировании удаляет файл сразу; «Отмена» не откатывает | `onClear = { PhotoStore.delete(frontPhotoPath); frontPhotoPath = null }` — нет ветки `existing != null` (ср. аккуратный `orphanPaths` при замене, стр. 267) |
| 5 | high | утечка | `ui/scan/PhotoCaptureScreen.kt:101` | Камера не отвязывается при закрытии экрана съёмки | `provider.bindToLifecycle(lifecycleOwner, ...)` к Activity-lifecycle; `unbindAll` вызывается только перед новой привязкой (стр. 100), `onDispose`-очистки нет |
| 6 | high | баг | `data/BarcodeDecoder.kt:56` | OOM при декоде больших изображений на API 28+ | `ImageDecoder.decodeBitmap(source) { ... }` — без ограничения размера (в ветке API 26–27 даунсемпл есть, стр. 64–67) |
| 7 | high | баг | `ui/screens/AddCardScreen.kt:313` | После поворота экран обрезки получает битмап 1×1 | `pendingCropBitmap ?: Bitmap.createBitmap(1, 1, ...)`; сам `pendingCropBitmap` — обычный `remember` (стр. 161), а `croppingSide` — saveable (стр. 160) |
| 8 | high | баг | `ui/screens/AddCardScreen.kt:300` | После снимка форма «висит» без индикатора до 4 прогонов ML Kit; кнопка «Сохранить» активна → гонка | `pendingBarcode = BarcodeDecoder.decodeBestEffort(context, bitmap)`; `croppingSide = side` ставится только после декода |
| 9 | medium | уязвимость | `data/PhotoStore.kt:32` | Удаление файла по пути из БД без проверки каталога | `if (path != null) File(path).delete()` — нет проверки принадлежности `filesDir/photos` |
| 10 | medium | уязвимость | `AndroidManifest.xml:10` | Данные карт (БД+фото) уезжают в облачный бэкап | `android:allowBackup="true"` без `fullBackupContent`/`dataExtractionRules` |
| 11 | medium | логика | `ui/screens/SettingsScreen.kt:214` | Импорт CSV создаёт дубли, вставки не в одной транзакции | `for (card in parsed.cards) { repository.add(card) }` — без дедупликации и `@Transaction` |
| 12 | medium | уязвимость | `data/CsvCodec.kt:34` | CSV-инъекция при открытии экспорта в Excel | `escape` экранирует только `; , " \n \r`; ведущие `=`, `+`, `-`, `@` не нейтрализуются |
| 13 | medium | баг | `ui/screens/HomeScreen.kt:92` | Дисковый IO и рендер кода на главном потоке | `remember(card.frontPhotoPath) { PhotoStore.load(card.frontPhotoPath, ...) }` в сетке; аналогично `CardScreen.kt:126`, `AddCardScreen.kt:179` |
| 14 | medium | баг | `ui/screens/AddCardScreen.kt:176` | Stale-предпросмотр кода: неполный ключ remember | `remember(codeValue) { ... render(codeValue, resolvedType, detectedFormat ...) }` — тип и формат не в ключе |
| 15 | medium | логика | `data/CodeRenderer.kt:58` | PDF417/AZTEC/DATA_MATRIX молча перерисовываются в CODE_128 | `else -> return null` → fallback `encode1D(value, "CODE_128", ...)` (стр. 36), при том что формат сохраняется в БД (`BarcodeFormats.kt:19-21`) |
| 16 | medium | логика | `MainActivity.kt:52` | Edit передаёт снапшот Card, а не id; update удалённой карты молча ничего не делает | `data class Edit(val card: Card)`; `CardDao.kt:25` `@Update suspend fun update(card: Card)` без проверки результата |
| 17 | medium | логика | `ui/AddCardViewModel.kt:51` | Файлы фото удаляются до удаления записи БД — сбой БД оставит карту без файлов | `PhotoStore.delete(card.frontPhotoPath)` → `repository.delete(card)` |
| 18 | medium | баг | `ui/screens/AddCardScreen.kt:440` | Двойной тап «Сохранить» создаёт две карты (нет блокировки кнопки) | `onClick = { saveCard() }` — кнопка не дизейблится на время асинхронного `viewModel.save` |
| 19 | medium | баг | `data/BarcodeDecoder.kt:28` | Новый (тяжёлый) клиент ML Kit на каждую попытку декода; до 4 шт. на фото | `val scanner = BarcodeScanning.getClient(...)` внутри `decode` при каждом вызове |
| 20 | medium | цикл | `ui/scan/PhotoCaptureScreen.kt:89` | `busy` без таймаута: незашедший колбэк камеры оставляет затвор мёртвым навсегда | `var busy by remember { mutableStateOf(false) }`; сброс только в `onImageSaved`/`onError` |
| 21 | medium | мусор | `res/values/strings.xml:3` | Все строки UI захардкожены в Kotlin (лока­лизация невозможна) | в `strings.xml` только `<string name="app_name">Мои карты</string>` |
| 22 | low | мусор | `data/CodeRenderer.kt:4,11` | Дублированные импорты (3 места в проекте) | `import androidx.core.graphics.createBitmap` дважды; ср. `CardDetailViewModel.kt:5,15`, `SettingsScreen.kt:35,36` |
| 23 | low | логика | `data/PhotoStore.kt:104` | EXIF TRANSPOSE/TRANSVERSE не учитываются — фото может быть повёрнуто | `ORIENTATION_ROTATE_90 -> 90f` … `else -> 0f`; требует проверки на устройстве |
| 24 | low | логика | `data/CodeTypeResolver.kt:13` | Эвристика «http…» ловит не-URL значения | `v.startsWith("http")` — «httpfoo» станет QR |
| 25 | low | мусор | `data/PhotoStore.kt:17` | Каталог `captures/` не чистится от сирот (смерть процесса между снимком и удалением) | файл создаётся `newCaptureFile`, удаляется только в колбэках `PhotoCaptureScreen.kt:121,136` |
| 26 | low | мусор | `app/src/test/.../MainActivityTest.kt:22` | Smoke-тест без единого assert | `ActivityScenario.launch(...).use { ... }` — нет проверок |
| 27 | low | логика | `MainActivity.kt:135` | В режимах Detail/Edit ни один пункт нижнего меню не подсвечен | `BottomItem(..., current is Screen.Add)` / `current is Screen.Settings` — других веток нет |
| 28 | low | логика | `data/AppSettings.kt:38` | Запись настроек до `init` молча теряется | `prefs?.edit()?.putBoolean(...)?.apply()` — `prefs` может быть null |
| 29 | low | мусор | `app/build.gradle.kts:16` | Заметка: versionCode=22 при versionName=«1.0» (комментарий упоминает v0.7.1) — не рассинхрон, но неочевидная история версий; APK подтверждает 22/1.0 | `versionCode = 22`, `versionName = "1.0"` |

Счёт: **2 critical, 6 high, 13 medium, 8 low**.

---

## 3. Детали и рекомендации по категориям

### 3.1. Critical

**№1. Краш рендера кода (баг).**
`CardScreen.kt:125-127` — `remember(...) { CodeRenderer.render(current.codeValue, current.codeType, current.barcodeFormat) }` без обработки исключений. `CodeRenderer.render` (`CodeRenderer.kt:28-43`) для BARCODE идёт по цепочке: `encode1D(value, формат)` (внутри try, возвращает null) → `encode1D(value, "CODE_128")` (тоже в try) → **финальный** `MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, ...)` — уже **без try**. ZXing `Code128Writer` (core 3.5.3) бросает `WriterException` для символов вне ISO-8859-1 и для строк длиннее 80 символов. Сценарии воспроизведения:
- ручной ввод: `canSave = storeName.isNotBlank()` (`AddCardScreen.kt:165`) — значение не проверяется; предпросмотр обёрнут в `try` (`AddCardScreen.kt:178-182`), поэтому карта спокойно сохраняется, а падает уже экран карты;
- импорт CSV: `CsvCodec.parse` (`CsvCodec.kt:65-73`) не валидирует `codeValue` — файл с `BARCODE` + кириллицей импортируется, и приложение падает при открытии любой такой карты. То же в полноэкранном режиме: `CardScreen.kt:349-357` — рендер без try.
*Рекомендация:* обернуть `render` в `runCatching` на уровне `CodeRenderer.render` и возвращать null/fallback (для не-ISO-8859-1 кодировать в CODE_128 нельзя — показывать предупреждение при сохранении и при импорте); добавить юнит-тест на кириллицу/пустую строку/длину >80.

**№2. Подпись релиза debug-ключом (уязвимость).**
`app/build.gradle.kts:33-42`: `release { ... signingConfig = signingConfigs.getByName("debug") }`. Подтверждено по APK: блок подписи v2/v3 присутствует, в сертификате найден маркер `Android Debug`. Debug-ключ генерируется одинаковым для всех машин шаблоном и не предназначен для распространения: Google Play такой релиз не примет, а при распространении APK «как есть» отсутствует защита от подмены.
*Рекомендация:* создать release-keystore (и хранить отдельно от репозитория), прописать отдельный `signingConfigs.create("release")`; до этого не публиковать сборку.

### 3.2. High

**№3. Навигация и состояние экранов (баг жизненного цикла).**
`MainActivity.kt:73` — `var screen by remember { mutableStateOf<Screen>(Screen.Home) }`. Любое пересоздание Activity (поворот, тёмная тема, нехватка памяти) сбрасывает на Home: теряются заполненная форма добавления/редактирования (имя, фото-пути, код), открытая карта, полноэкранные режимы. Комментарий признаёт: «rememberSaveable здесь падал бы» — но проблема решается кастомным `Saver`/`Bundle`-маппингом (сериализовать тип экрана + cardId, а не объект).
*Рекомендация:* `rememberSaveable(stateSaver = ...)` с маппингом Screen → Bundle (Detail/Edit хранить по `cardId`; для Edit — id, данные поднимать из БД), либо перейти на Navigation-библиотеку с восстановлением стека.

**№4. Потеря фото при «убрать фото» + «Отмена» (логика домена).**
Замена фото сделана аккуратно: старый файл либо удаляется сразу (новая карта), либо складывается в `orphanPaths` и удаляется только после сохранения (`AddCardScreen.kt:266-268, 277-279`). Крестик же (`AddCardScreen.kt:484-487, 495-498`) вызывает `PhotoStore.delete(...)` немедленно **без ветки `existing`**: в редактировании «крестик → Отмена» оставляет в БД путь на удалённый файл — фото карты потеряно безвозвратно (`CardScreen` покажет пустой `Box`, `CardScreen.kt:232-234`).
*Рекомендация:* в режиме редактирования крестик должен лишь обнулять путь в состоянии формы и класть путь в `orphanPaths`, а фактическое удаление — после успешного сохранения (симметрично замене).

**№5. Утечка камеры (утечка ресурса).**
`PhotoCaptureScreen.kt:92-108`: в `LaunchedEffect(hasPermission)` камера привязывается `provider.bindToLifecycle(lifecycleOwner, ...)`, где `lifecycleOwner` — Activity. При закрытии диалога съёмки композиция умирает, но привязка остаётся до DESTROY Activity или до следующего `provider.unbindAll()` (стр. 100, вызывается только при повторном входе). Всё это время камера активна (сенсор, энергия). Поведение CameraX по документации; на устройстве не проверялось — помечаю как статически подтверждённое отсутствие отвязки.
*Рекомендация:* `DisposableEffect`/`onDispose { provider.unbindAll() }` (или привязка к lifecycle окна диалога), плюс отмена привязки при `hasPermission == false`.

**№6. OOM при выборе изображения (баг).**
`BarcodeDecoder.kt:53-59`: на API 28+ `ImageDecoder.decodeBitmap(source) { decoder.allocator = ALLOCATOR_SOFTWARE }` декодирует изображение **в исходном разрешении** — для 50–200 Мп фото это сотни МБ. Даунсемпл до 2048 есть только в ветке API 26–27 (`BarcodeDecoder.kt:60-72`). Триггер — кнопки «Код из файла» (`AddCardScreen.kt:325`) и «Из галереи» (там `PhotoStore.loadFromUri` ограничен 2048, безопасно).
*Рекомендация:* задать `decoder.setTargetSampleSize`/`setTargetResolution` ~2048 по большей стороне, как в остальных загрузчиках проекта.

**№7. Обрезка после поворота получает битмап 1×1 (баг).**
`croppingSide` — `rememberSaveable` (`AddCardScreen.kt:160`), а `pendingCropBitmap`/`pendingBarcode` — обычный `remember` (стр. 161-162). Поворот в момент обрезки → bitmap == null → `AddCardScreen.kt:313` подставит `Bitmap.createBitmap(1, 1, ARGB_8888)` — пользователь увидит «обрезку» чёрного пикселя. Поворот при открытой камере ещё хуже: колбэк `onImageSaved` (`PhotoCaptureScreen.kt:118-132`) придёт в отменённый `scope` — снимок потерян, временный файл может остаться.
*Рекомендация:* закрывать стадии (`capturingSide`/`croppingSide`) в `onCancel`/пересоздании (или пушить фото-стадию в saveable-стейт с путем к файлу, а не битмапу в памяти); при показе обрезки проверить `pendingCropBitmap == null` → сбросить стадию вместо заглушки 1×1.

**№8. «Подвисание» после снимка и гонка сохранения (баг/гонка).**
`AddCardScreen.kt:297-304`: после снимка `capturingSide = null` (пользователь видит форму), и только после `decodeBestEffort` (до 4 полных прогонов ML Kit с созданием 4 сканеров, `BarcodeDecoder.kt:42-51`) открывается обрезка. Всё это время нет индикатора прогресса, а «Сохранить» активно: быстрое сохранение фиксирует карту **без** нового фото, а `onCropDone` (`AddCardScreen.kt:253-287`) продолжит работу в корутине уничтожаемого экрана — файл фото станет сиротой.
*Рекомендация:* блокировать форму (или показывать прогресс) на время декода; состояние «фото в обработке» делать частью saveable-стейта и проверять его в `saveCard`.

### 3.3. Medium

**№9. PhotoStore.delete без проверки пути (уязвимость, defense-in-depth).**
`PhotoStore.kt:31-33`: `File(path).delete()` по абсолютному пути из БД. Прямой инъекции извне сейчас нет (CSV пути не переносит, exported-компонентов нет), но в связке с `allowBackup=true` (№10) подменённая БД превращает это в «удалить произвольный файл по пути из записи». *Рекомендация:* валидировать `canonicalFile` внутри `filesDir/photos`, игнорировать чужое.

**№10. allowBackup=true (уязвимость приватности).**
`AndroidManifest.xml:10`. БД со значениями карт и `files/photos` уходят в Google Auto Backup без ограничений. Для офлайн-хранилища бонусных карт это нежелательно. *Рекомендация:* `dataExtractionRules`/`fullBackupContent` с исключением БД и фото (или `allowBackup=false`).

**№11. Импорт CSV: дубли и неатомарность (логика).**
`SettingsScreen.kt:213-217` — цикл вставок без транзакции и без проверки существующих (импорт того же файла дважды = полный дубль всех карт; прерывание на середине = частичный импорт без отката). *Рекомендация:* `@Transaction`-метод массовой вставки + дедупликация по (storeName, codeValue) с отчётом пользователю.

**№12. CSV-инъекция (уязвимость).**
`CsvCodec.kt:34-41`: escape не трогает ведущие `=`, `+`, `-`, `@` — название магазина вида `=HYPERLINK(...)` исполнится формулой при открытии экспорта в Excel (формат заявлен «для Excel», строка 6 файла). *Рекомендация:* экранировать опасный первый символ (префикс `'`).

**№13. IO и рендер на главном потоке (баг производительности).**
- `HomeScreen.kt:92-97`, `CardScreen.kt:221,247`, `AddCardScreen.kt:610` — JPEG-декод в `remember` в композиции (в сетке — на каждую ячейку);
- `AddCardScreen.kt:176-183` — рендер ZXing на каждый введённый символ;
- `CardScreen.kt:125-127, 349-357` — рендер до 1400×1400 при открытии.
При десятках карт и длинных значениях это jank вплоть до ANR. *Рекомендация:* `produceState`/`LaunchedEffect` + `Dispatchers.Default/IO`, кэш битмапов, debounce ввода.

**№14. Stale-предпросмотр кода (баг).**
`AddCardScreen.kt:176`: `remember(codeValue)` — ключ не включает `resolvedType`/`detectedFormat`. Если «Код из файла» распознаёт то же значение с другим форматом, предпросмотр останется старым, хотя в БД уйдёт новый `barcodeFormat` — нарушена гарантия «что вижу, то сохранится». *Рекомендация:* добавить оба параметра в ключ.

**№15. PDF417/AZTEC/DATA_MATRIX → CODE_128 (логика домена).**
`BarcodeFormats.kt:19-21` сохраняет эти форматы в БД, `CodeTypeResolver.kt:9` относит их к BARCODE, но `CodeRenderer.kt:47-59` их не знает → молчаливый fallback в CODE_128 (`CodeRenderer.kt:36-37`). Карта с PDF417 (например, длинный номер) перерисуется чужим форматом — на кассе не прочитается. *Рекомендация:* поддержать рендер этих форматов в ZXing или явно предупреждать пользователя.

**№16. Edit по снапшоту Card (логика).**
`MainActivity.kt:52` `data class Edit(val card: Card)` — в форму уходит копия; `CardDao.kt:25-26` `@Update` при удалённой записи молча обновит 0 строк — пользователь не узнает, что правки не применились. Объект также не переживёт сохранение состояния (не Parcelable), усугубляя №3. *Рекомендация:* передавать `cardId` и грузить свежие данные через Flow.

**№17. Порядок «файлы → запись БД» при удалении (логика).**
`AddCardViewModel.kt:49-56` и `CardDetailViewModel.kt:31-37`: фото удаляются до `repository.delete`; сбой БД оставит карту с битыми путями. *Рекомендация:* сначала запись, потом файлы (или транзакция с откатом).

**№18. Двойное сохранение (баг/гонка).**
`AddCardScreen.kt:439-441`: галочка не блокируется на время асинхронного `viewModel.save` — быстрый двойной тап для новой карты даёт два `insert` → две одинаковые карты. Статически возможно; вероятность требует проверки вручную. *Рекомендация:* флаг `saving` в стейте формы + `enabled = !saving`.

**№19. Создание сканера ML Kit на каждый вызов (баг производительности).**
`BarcodeDecoder.kt:28` — `BarcodeScanning.getClient(...)` на каждый `decode`, а `decodeBestEffort` (стр. 42-51) делает до 4 инициализаций тяжёлого клиента на одно фото. *Рекомендация:* один клиент на серию попыток (передавать снаружи или кэшировать с `close` в конце).

**№20. Busy без таймаута (зависание).**
`PhotoCaptureScreen.kt:89,110-141`: если CameraX не вызовет ни `onImageSaved`, ни `onError` (сбой драйвера), `busy=true` навсегда — затвор мёртв, выхода кроме закрытия нет. *Рекомендация:* таймаут (например, 10 с) со сбросом `busy` и тостом.

**№21. Строки и цвета захардкожены (мусор).**
`strings.xml:3` — единственная строка; весь UI-текст — литералы в Kotlin (например, `PhotoCaptureScreen.kt:162` «Нужно разрешение на камеру»), цвета вне темы: `Color(0xFFC65B4E)` (`AddCardScreen.kt:387,421`), `Color(0xFF1C1B22)` (стр. 625-633). *Рекомендация:* перенести в ресурсы (`strings.xml`, палитра темы).

### 3.4. Low

- **№22. Дублированные импорты** — `CodeRenderer.kt:4,11` (`createBitmap`), `CardDetailViewModel.kt:5,15` (`viewModelScope`), `SettingsScreen.kt:35,36` (`setValue`). Убрать.
- **№23. EXIF-поворот**: `PhotoStore.kt:94-108` обрабатывает только ROTATE_90/180/270; TRANSPOSE/TRANSVERSE дадут повёрнутое фото. Требует проверки на устройстве (зависит от камеры).
- **№24. `resolveCodeType`**: `CodeTypeResolver.kt:13` `v.startsWith("http")` — «httpfoo» ошибочно станет QR. Сузить до `http://`/`https://` или `Patterns.WEB_URL`.
- **№25. Сироты `captures/`**: `PhotoStore.kt:16-18` создаёт файлы, чистятся только в колбэках (`PhotoCaptureScreen.kt:121,136`); смерть процесса между ними оставляет мусор. Чистить каталог при старте.
- **№26. `MainActivityTest`** (`MainActivityTest.kt:20-33`) — smoke без assert'ов; полезен, но не ловит регрессии. Добавить проверки (например, наличие decorView-детей).
- **№27. BottomBar**: `MainActivity.kt:135-136` — в Detail/Edit ни один пункт не выделен. Косметика UX.
- **№28. AppSettings до init**: `AppSettings.kt:36-44` — `prefs?.edit()` молча теряет запись, если сеттер вызван до `init` (сейчас `init` в `onCreate` раньше композиции — безопасно, но хрупко).
- **№29. История версий**: `versionCode = 22` при `versionName = "1.0"` (`app/build.gradle.kts:16-17`) — APK подтверждает 22/1.0, рассинхрона нет; неочевидно, закомментировано «v0.7.1» (стр. 25). Рекомендация: синхронизировать нумерацию или описать схему.

### 3.5. Согласованность (манифест ↔ build ↔ код ↔ APK)

- `applicationId`/`package` = `com.tashik54.cardholder` — совпадает в `app/build.gradle.kts:13` и бинарном манифесте APK. ✔
- `minSdk 26 / target 34 / compileSdk 34` — совпадают в gradle и APK-манифесте. ✔
- `versionCode 22 / versionName 1.0` — gradle = APK. ✔
- Разрешения: только `CAMERA` (`AndroidManifest.xml:4`) + `uses-feature camera required=false` — минимальны и соответствуют коду (камера опциональна: ветка «Нужно разрешение»). Лишних разрешений нет, `INTERNET` отсутствует — ML Kit bundled (нативные `lib/*/libbarhopper_v3.so` и tflite-модели в APK подтверждены). ✔
- Exported-компоненты: единственный `MainActivity` с MAIN/LAUNCHER — норма. ✔
- `isMinifyEnabled = true` при пустом `proguard-rules.pro` («пока стандартные») — работает за счёт consumer-правил библиотек; проверка R8-результата не проводилась.
- Room: все запросы параметризованы (`CardDao.kt:13-29`), SQL-инъекций нет; `exportSchema = false` при version=1 — допустимо.
- Подпись: **несоответствие ожиданиям релиза** — APK подписан debug-сертификатом (см. №2).

---

## 4. Что НЕ проверено и почему

1. **Динамическое поведение** (реальные краши, ANR, поведение камер конкретных прошивок) — аудит по правилам не запускал ни сборку, ни приложение, ни тесты; все выводы о рантайме — статические (в №5, №18, №23 помечено «требует проверки на устройстве»).
2. **Декомпиляция classes.dex APK** — R8-минифицированный код не сверялся с исходниками построчно; APK проверен только на уровне манифеста, подписи и состава архива (библиотеки, модели ML Kit).
3. **Целостность `gradle-wrapper.jar`** (сравнение хэша с официальным дистрибутивом) — вне зоны аудита; сборка не выполнялась.
4. **Папки `design/`, `docs/`** — просмотрены поверхностно: на APK и поведение кода не влияют.
5. **Тесты не запускались** (`CodeTypeResolverTest`, `CsvCodecTest`, `MainActivityTest`) — читаны статически; покрытие оценено качественно.
6. **Криптостойкость/устройство debug-хранилища ключей** — проверял только маркер сертификата «Android Debug» в APK, full-разбор PKCS#7 не делался.

---

*Отчёт составлен статическим анализом; каждое замечание привязано к файлу:строке по текущему состоянию исходников. Код проекта не изменялся.*
