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

- Offline-first: приложение работает без аккаунта, данные хранятся в Room (`data/local/AppDatabase.kt`).
- Google Sign-In опционален и нужен только для бэкапа в Firestore (`users/{uid}`).
- Навигация через Compose Navigation: `main` (список) → `add_edit/{id?}` (форма) / `profile`.
- `ExerciseViewModel` (`ui/viewmodel/`) хранит список упражнений в `StateFlow` через `stateIn`.
- `utils/DistributionHelper.kt` — заготовка под расчёт подходов с учётом сна.

## Конфигурация агента

Правила работы находятся в `AGENT.md` (не коммитится, добавлен в `.gitignore`).
