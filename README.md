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

- Offline-first: приложение работает без аккаунта, данные хранятся в Room (`data/local/AppDatabase.kt`, версия 5).
- Модель: `WorkoutEntity` (тренировка) ↔ `ExerciseEntity` (упражнения) — many-to-many через `WorkoutExerciseCrossRef`.
- `ProgressEntity` хранит выполненные подходы `List<Int>` по дате и упражнению (в БД CSV через `Converters`).
- Google Sign-In опционален и нужен только для бэкапа в Firestore (`users/{uid}`).
- `data/sync/SyncManager.kt` — push/pull тренировок и упражнений в `users/{uid}/workouts/{workoutId}/exercises/{exerciseId}` и прогресса в `users/{uid}/progress/{YYYY-MM-DD_exerciseId}`. При конфликте тренировок облако перезаписывает локальные данные; для прогресса побеждает бо́льшее число выполненных подходов. Прогресс пушится при отметке чекбокса, подтягивается при входе, смене месяца в истории и ручной синхронизации. Отметка времени последней синхронизации хранится в SharedPreferences и показывается в профиле.
- Навигация: `main` → `workout/{id}` / `exercise/{id}` / `profile`.
- `utils/DistributionHelper.kt` — расчёт нормы с учётом времени сна (`sleepStart`/`sleepEnd`), `floor`, перенос остатков и `splitIntoSets` (по 20 повторений в подходе).
- `TodayViewModel` показывает только тренировки, активные на сегодня; чекбоксы подходов на экране «Сегодня».
- `MainScreen` — вкладки «Сегодня», «Все тренировки», «История» (календарь с цветовой индикацией прогресса).
- Миграции Room 1→2, 2→3, 3→4 и 4→5 сохраняют данные (3→4 обнуляет старые счётчики повторений, так как они не разбиты на подходы; 4→5 добавляет `restTimeMinutes = 30`).
- `NotificationWorker` (WorkManager) показывает уведомление «Пора делать подход!» каждые 30 минут; разрешение `POST_NOTIFICATIONS` запрашивается на Android 13+.
- В карточке тренировки после отметки подхода запускается таймер отдыха на `restTimeMinutes`.
- Дизайн: собственный адаптивный vector icon (гантель на синем фоне `#1A73E8`), Material 3 палитра primary `#3B82F6` / secondary `#10B981` / tertiary `#F59E0B` / error `#EF4444`, скругления 16.dp, жирные заголовки.
- UI: `Crossfade` между вкладками, карточки с elevation 4.dp, подходы в виде «пилюль» с анимацией цвета, круговой индикатор отдыха, календарь с выделением выходных и текущего дня.
- При запуске показывается Compose splash-экран с логотипом и названием «Setly» (1.2 c), затем открывается `main`.

## Конфигурация агента

Правила работы находятся в `AGENT.md` (не коммитится, добавлен в `.gitignore`).
