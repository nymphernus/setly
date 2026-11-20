# Setly

Трекер тренировок с синхронизацией (Kotlin + Jetpack Compose, Room, Firebase).

## Стек и структура

- `app/src/main/java/com/nmph/setly/` — код приложения (пакет `com.nmph.setly`).
  - `MainActivity.kt` — точка входа, Compose-экран.
  - `ui/theme/` — тема Material 3.
- Зависимости описаны в `gradle/libs.versions.toml` (version catalog).
- Локальная БД: Room. Облако: Firebase Auth + Firestore (`users/{uid}/...`).
- Фоновые задачи: WorkManager.

## Сборка

Требуются JDK 17 и Android SDK (compileSdk 37, minSdk 29, targetSdk 34).
Целевой `jvmTarget` — 17. На машине без отдельного JDK 17 можно использовать JBR из Android Studio:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

В PowerShell на Windows использовать `.\gradlew` (не `gradlew`):

```powershell
.\gradlew assembleDebug
.\gradlew test
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

AGP 9.5.1 по умолчанию использует встроенный Kotlin и новый DSL, несовместимые с KSP (Room codegen).
Поэтому в `gradle.properties` выставлены `android.builtInKotlin=false` и `android.newDsl=false` —
это временный обход до миграции на новый DSL; сборка проходит с предупреждениями об устаревании.

## Firebase

- `google-services.json` лежит в `app/` и подключается плагином `com.google.gms.google-services`.
- SHA-1 добавлен в консоли Firebase; Google Sign-In включён в Authentication.
- Web Client ID для `requestIdToken` хранится в `res/values/strings.xml` (`google_web_client_id`).
- Файл `google-services.json` не удалять и не перезаписывать без согласования.

## Архитектура

- Offline-first: приложение работает без аккаунта, данные хранятся в Room (`data/local/AppDatabase.kt`, версия 4).
- Модель: `WorkoutEntity` (тренировка) ↔ `ExerciseEntity` (упражнения) — many-to-many через `WorkoutExerciseCrossRef`.
- `ProgressEntity` хранит выполненные подходы `List<Int>` по дате и упражнению (в БД CSV через `Converters`).
- Google Sign-In опционален и нужен только для бэкапа в Firestore (`users/{uid}`).
- Навигация: `main` → `workout/{id}` / `exercise/{id}` / `profile`.
- `utils/DistributionHelper.kt` — расчёт нормы с учётом времени сна (`sleepStart`/`sleepEnd`), `floor`, перенос остатков и `splitIntoSets` (по 20 повторений в подходе).
- `TodayViewModel` показывает только тренировки, активные на сегодня; чекбоксы подходов на экране «Сегодня».
- `MainScreen` — вкладки «Сегодня», «Все тренировки», «История» (календарь с цветовой индикацией прогресса).
- Миграции Room 1→2, 2→3 и 3→4 сохраняют данные (3→4 обнуляет старые счётчики повторений, так как они не разбиты на подходы).

## Конфигурация агента

Правила работы находятся в `AGENT.md` (не коммитится, добавлен в `.gitignore`).
