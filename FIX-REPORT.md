# FIX-REPORT — «Мои карты» v1.0.1 (исправления по аудиту v1.0)

Дата: 2026-11-06
Проект: `E:\AI\ZCode\проекты\cardholder\cardholder-fixed` (копия проекта v1.0 + исправления по AUDIT-REPORT.md).
Результат: **APK собран, подписан release-ключом, все 20 юнит-тестов зелёные.**

## Сборка

| Параметр | v1.0 (было) | v1.0.1 (стало) |
|---|---|---|
| APK | MyCards-v1.0-release.apk (24,5 МБ) | **MyCards-v1.0.1-release.apk (24,1 МБ)** в корне этой папки |
| versionCode / versionName | 22 / 1.0 | **23 / 1.0.1** (проверено по бинарному манифесту APK; semver-патч поверх 1.0) |
| Подпись | debug-сертификат («Android Debug») | **собственный ключ `CN=Cardholder`**, схема v2 (проверено apksigner: `Verifies`, v2=true) |
| Минификация | R8 | R8 (без изменений) |
| Тесты | 15 (3 файла) | **20 (4 файла), все зелёные** |

Сборка выполнена через ASCII-джанкшн `E:\AI\ZCode\tools\cardholder-build` → эта папка:
(AGP 8.5.2 отвергает не-ASCII пути). Для будущих пересборок:
AGP 8.5.2 отвергает не-ASCII пути. Для будущих пересборок:
```bat
cd /d E:\AI\ZCode\tools\cardholder-build
set JAVA_HOME=E:\AI\ZCode\tools\jdk17
gradlew.bat assembleRelease
```
(аппаратно это та же папка — изменения видны в обеих точках).

Подпись: `keystore/release.jks` (RSA 2048, действует до 2054), параметры в `keystore/keystore.properties`.
Оба файла в `.gitignore`. Пароль — в `E:\AI\ZCode\Записки Дену\свистопляска.md`.
**Ключ новый — старые установки v1.0 поверх не обновятся** (другая подпись), ставить с удалением старой версии.

## Что исправлено (все 29 пунктов аудита)

### Critical
1. **C1 Краш рендера кода** — `CodeRenderer.render` теперь возвращает `Bitmap?`: все encode-попытки через `tryEncode` (единый catch), пустое/некодируемое значение → null; UI (`CardScreen`) показывает сообщение «Значение нельзя показать кодом» вместо падения. Бонус: PDF417/AZTEC/DATA_MATRIX рендерятся своими форматами ZXing, а не молча CODE_128 (№15). Регресс-тесты: `CodeRendererTest` (8 тестов, Robolectric).
2. **C2 Debug-подпись релиза** — `buildTypes.release` подписывается новым конфигом `signingConfigs.release` (keystore/release.jks). При отсутствии keystore-файла сборка падает, а не молча подписывает debug-ключом.

### High
3. **H1 Навигация терялась при повороте** — `Screen` сохраняется через `rememberSaveable` с кастомным `listSaver` (тип экрана + cardId в Bundle); Edit хранит id, форма получает живые данные из Flow БД (№16 закрыт тут же).
4. **H2 Крестик фото удалял файл сразу** — в режиме редактирования файл переносится в `orphanPaths`, реальное удаление — только после успешного сохранения; «Отмена» теперь безопасна.
5. **H3 Камера не отвязывалась** — `DisposableEffect.onDispose { provider.unbindAll() }` + защита от колбэка после ухода с экрана (AtomicBoolean); превью камеры больше не живёт после закрытия окна съёмки.
6. **H4 OOM на больших изображениях** — `BarcodeDecoder.loadBitmap` на API 28+ ограничивает декод через `setTargetSampleSize` до 2048 по большей стороне (как и ветка 26–27).
7. **H5 Обрезка после поворота (битмап 1×1)** — `LaunchedEffect` сбрасывает стадию обрезки, если битмап не пережил пересоздание; заглушка 1×1 удалена.
8. **H6 Гонка после снимка** — обрезка открывается сразу, ML Kit работает параллельно; флаг `photoProcessing` блокирует «Сохранить» на время обработки, флаг `saving` защищает от двойного тапа → двух карт (№18 закрыт тут же).

### Medium
9. **`PhotoStore.delete`** — принимает Context, удаляет только файлы внутри `files/photos` (canonical-проверка), чужие пути игнорируются.
10. **`allowBackup`** — отключён в манифесте (данные карт не уезжают в облачный бэкап).
11. **Импорт CSV** — дедупликация по паре «название+код» и атомарная вставка `@Transaction insertAll` в `CardRepository.importCards`; тост показывает и число дубликатов.
12. **CSV-инъекция** — `CsvCodec.escape` ставит защитный апостроф перед `= + - @ TAB`; при разборе апостроф снимается (round-trip без потерь; тест `csv-инъекция нейтрализуется...`).
13. **IO/рендер на главном потоке** — новый `AsyncGraphics.kt`: `rememberPhoto` (produceState + Dispatchers.IO) для всех фото (сетка, карточка, просмотр) и `rememberCodeBitmap` (Dispatchers.Default) для всех рендеров кода.
14. **Stale-предпросмотр** — ключ рендера включает значение+тип+формат (через produceState-ключи).
15. — закрыт в №1 (свои форматы для 2D-кодов).
16. — закрыт в №3 (Edit по id).
17. **Порядок удаления** — `CardRepository.deleteCardWithPhotos`: сначала БД, потом файлы.
18. — закрыт в №8.
19. **ML Kit-сканер на каждый вызов** — `decodeBestEffort` создаёт один клиент на все 4 попытки и закрывает его в finally.
20. **`busy` без таймаута** — 10-секундный таймер снимает блокировку затвора с тостом «Камера не ответила».
21. **Захардкоженные строки/цвета** — весь UI-текст в `strings.xml` (~70 строк через `stringResource`); `AccentDanger`/`OnPhotoBadge` в теме.

### Low
22. Дублированные импорты удалены (CodeRenderer, CardDetailViewModel, SettingsScreen).
23. EXIF TRANSPOSE/TRANSVERSE обрабатываются (поворот + отражение).
24. `resolveCodeType`: только `http://`, `https://`, `www.`, `://` (тест на «httpfoo»).
25. `captures/` чистится при старте (`cleanupCaptures` в `onCreate`).
26. `MainActivityTest` получил assert'ы (RESUMED + decorView).
27. Нижнее меню скрывается на Detail/Edit (некорректная подсветка исчезла вместе с меню).
28. `AppSettings` хранит applicationContext и берёт prefs лениво — записи до init не теряются.
 29. Схема версий задокументирована в gradle: v1.0.1 = код 23 (semver-патч).

## Тесты (20, все зелёные)

- `CsvCodecTest` — 7 (вкл. новый на CSV-инъекцию)
- `CodeTypeResolverTest` — 4 (вкл. новый на «httpfoo»)
- `CodeRendererTest` — 8 (новый файл: кириллица/пустое/длинные/QR/PDF417/AZTEC/DATA_MATRIX/fallback)
- `MainActivityTest` — 1 (Robolectric smoke + assert'ы)

## Изменённые файлы

19 из 22 Kotlin-файлов + `strings.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`, `.gitignore`; новые: `AsyncGraphics.kt`, `CodeRendererTest.kt`, `keystore/` (2 файла). `PhotoCropScreen`, `Theme.kt`, тесты — точечные правки. Удалено: старый APK v1.0 из корня, дубль AUDIT-REPORT.

## Публикация

- Коммит `c7db639` «v1.1: fix all 29 audit findings …» и `81f6fd2` «chore: versionName 1.0.1 (semver patch)» запушены в `main` репозитория `tashik54-tatar/cardholder`.
- Релиз: **https://github.com/tashik54-tatar/cardholder/releases/tag/v1.0.1** с ассетом `MyCards-v1.0.1-release.apk` (24,1 МБ).
- Промежуточно публиковавшийся релиз «v1.1» удалён вместе с тегом (переименование в semver-патч 1.0.1 по решению владельца).
- Исправленные исходники синхронизированы в корень репо; папка `cardholder-fixed/` остаётся рядом как локальная копия-поставка (в `.gitignore`).

## Что осталось (вне скоупа этого прохода)

- `LocalLifecycleOwner` deprecated (warning, не ошибка) — миграция на `androidx.lifecycle.compose.LocalLifecycleOwner` при обновлении lifecycle.
- Ручная проверка на устройстве: камера/повороты/фонарь прошивки — статически всё покрыто, динамически не гонялось.
- Обновление с v1.0 поверх не сработает (новый ключ подписи) — только удаление + установка v1.0.1.

---

# v1.0.2 (07.11) — фикс краша на повороте экрана

## Симптом

Приложение «закрывалось» при повороте телефона с любого экрана (главный,
настройки, страница карты, форма, камера, обрезка). Устройство: TECNO LI6,
Android 15.

## Диагноз

`java.lang.ClassCastException: java.lang.Long cannot be cast to java.lang.Integer`
в restore-лямбде `ScreenSaver` (`MainActivity.kt`, сохранение навигации через
`rememberSaveable`). Логирование подтвердило: приложение сохраняет
`listOf(0)` (Integer), но системная перезапаковка SavedState при пересоздании
активити возвращает числа из сохранённых списков другим типом — Long вместо
Int. Это особенность прошивки/системной перезапаковки на данном устройстве;
на стандартном пути Bundle (putInt/getInt) искажения нет — падали только
касты внутри кастомного Saver. Аудит v1.0.1 этого не покрывал: тип значения
при восстановлении нигде не проверялся.

## Фикс

`ScreenSaver` переведён с числовых тегов на строки
(`"home"/"add"/"settings"/"detail"/"edit"`, id карты — строкой): строки через
системную перезапаковку проходят без искажений. Malformed-состояние
(`as? String` / `toLongOrNull`) деградирует на главный экран вместо падения.
Поведение приложения не меняется.

## Проверка

- На устройстве (TECNO LI6, Android 15), adb + эмуляция поворота
  (`settings put system user_rotation`): повороты на главном (×2), настройках
  (×2), странице карты (×4), форме (×2) — 0 крашей, процесс не умирает,
  открытая карта восстанавливается после поворота.
- Unit-тесты 20/20 зелёные (запуск из ASCII-пути; через джанкшн
  cardholder-build тест-воркер не видит классы — особенность окружения).

## Публикация

- versionCode 25, versionName 1.0.2; APK `MyCards-v1.0.2-release.apk`
  (подпись CN=Cardholder, ставится поверх v1.0.1 обновлением).
- Релиз: https://github.com/tashik54-tatar/cardholder/releases/tag/v1.0.2
