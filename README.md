# Setly

**Setly** — offline-first трекер тренировок для Android. Приложение распределяет упражнения по дням цикла, считает дневную норму повторений с учётом сна, показывает подходы «пилюлями», ведёт таймер отдыха, хранит историю в календаре и по желанию синхронизирует данные с Firebase.

Версия: **1.0.0** (`versionCode = 1`) — первый релиз.

---

## Возможности

- Создание тренировок и упражнений, связь «многие ко многим».
- Расчёт дневной нормы: период тренировки, время сна, перенос недовыполненных повторений.
- Автоматический отдых `(24 * 60) / число подходов за день` или ручное значение в минутах; глобальный переключатель в профиле.
- Отметка подходов, круговой таймер отдыха, Snackbar по окончании отдыха.
- Завершение тренировки: все подходы дня отмечаются выполненными.
- История по месяцам с цветовой индикацией прогресса.
- Профиль: Google-аккаунт, статистика (тренировки, серия, повторения), настройки темы/уведомлений/авто-отдыха, ручная синхронизация.
- Нижняя навигация: «Тренировки», «История», «Профиль».
- Пустые состояния, анимированные карточки, Material 3 тема со светлым и тёмным вариантами.

---

## Стек технологий

| Область | Решение |
| --- | --- |
| Язык | Kotlin 2.x, JVM target 17 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Локальная БД | Room (схема v6), KSP, Flow |
| Облако | Firebase Authentication (Google Sign-In), Cloud Firestore |
| Фоновые задачи | WorkManager (периодические напоминания) |
| Настройки | SharedPreferences (`AppSettings`) |
| Асинхронность | Kotlin Coroutines, `StateFlow` / `collectAsStateWithLifecycle` |
| Сборка | Gradle 9.6.0, Android Gradle Plugin 9.5.1, version catalog (`gradle/libs.versions.toml`) |

Минимальная версия Android: **29**, целевая: **34**, compileSdk: **37**.

---

## Сборка

Требуются JDK 17 и Android SDK. Если отдельный JDK не установлен, можно использовать JBR из Android Studio:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

В PowerShell на Windows используйте `.\gradlew`:

```powershell
.\gradlew assembleDebug     # отладочный APK
.\gradlew test              # unit-тесты
.\gradlew assembleRelease   # релизный APK
```

Результаты:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

> В `gradle.properties` выставлены `android.builtInKotlin=false` и `android.newDsl=false`: AGP 9.5.1 по умолчанию включает встроенный Kotlin и новый DSL, пока несовместимые с KSP/Room. Это временный обход до миграции; сборка проходит с предупреждениями об устаревании.

### Подпись релиза

Сейчас `signingConfigs.release` использует стандартный `~/.android/debug.keystore` (alias `androiddebugkey`, пароли `android`) только для тестовой установки. **Перед публикацией замените его на собственный keystore и пароли.**

---

## Настройка Firebase

1. Создайте проект в [Firebase Console](https://console.firebase.google.com/).
2. Добавьте Android-приложение с пакетом `com.nmph.setly`.
3. Скачайте `google-services.json` и положите его в `app/`.
4. Добавьте SHA-1 сертификата в настройках Android-приложения Firebase:
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey
   ```
   Для собственного релизного keystore используйте его путь и alias.
5. В разделе **Authentication → Sign-in method** включите **Google**.
6. В `app/src/main/res/values/strings.xml` задайте `google_web_client_id` — Web Client ID из Firebase (не Android Client ID).
7. Включите Cloud Firestore и настройте правила доступа так, чтобы пользователь читал/писал только свой префикс `users/{uid}/...`.

Файл `app/google-services.json` в этом репозитории уже подключён плагином `com.google.gms.google-services`; не удаляйте и не перезаписывайте его без согласования.

---

## Архитектура

### Offline-First

Приложение полностью работоспособно без аккаунта и без сети: источник истины — локальная Room-БД `AppDatabase` (схема v6). Google Sign-In опционален и включается только для бэкапа.

Основные сущности:

- `WorkoutEntity` — тренировка (`periodDays`, `sleepStart/End`, `restTimeMinutes`, `autoRestTime`, `startDate`).
- `ExerciseEntity` — упражнение (`targetReps`, `repsPerSet`).
- `WorkoutExerciseCrossRef` — связь тренировка ↔ упражнение.
- `ProgressEntity` — выполненные подходы на дату (в БД хранятся CSV-списком через `Converters`).

Миграции 1→2 … 5→6 сохраняют данные; 5→6 добавляет `workouts.autoRestTime` и `exercises.repsPerSet`.

### Синхронизация

`data/sync/SyncManager.kt` работает с путями:

- `users/{uid}/workouts/{workoutId}` и `.../exercises/{exerciseId}`
- `users/{uid}/progress/{YYYY-MM-DD_exerciseId}`

Правила конфликтов:

- тренировки/упражнения: облачная версия перезаписывает локальную;
- прогресс: побеждает бо́льшее число выполненных подходов, при равенстве остаётся локальный вариант.

Синхронизация запускается при входе, смене месяца в истории, отметке подхода и вручную из профиля. Время последней синхронизации показывается в профиле.

### Распределение и отдых

`utils/DistributionHelper.kt`:

- `calculateTargetForToday` — дневная норма с коэффициентом сна и переносом остатка;
- `getSetsForExercise(exercise, targetRepsForToday, repsPerSet)` — `50 / 20 → [20, 20, 10]`;
- `effectiveRestMinutes(workout, totalSetsPerDay, autoRestEnabled)` — авто-расчёт или ручное значение; защита от деления на ноль возвращает 30 минут.

### Навигация и экраны

`MainActivity` содержит внешний `NavHost`: `splash` → `home_root`, `workout/{workoutId}`, `edit_workout/{workoutId}`, `exercise/{exerciseId}`. Внутри `MainScreen` — нижняя навигация `home`, `history`, `profile`; `innerPadding` `Scaffold` пробрасывается в `NavHost`, поэтому контент не перекрывается панелью.

`TodayViewModel` формирует список активных на сегодня тренировок и подходов; `WorkoutDetailScreen` отображает их в `LazyColumn`, поэтому длинный список прокручивается. Таймер отдыха живёт в Compose-состоянии экрана и перезапускается при отметке следующего подхода.

### Уведомления и настройки

`NotificationWorker` через WorkManager показывает напоминание каждые 30 минут. Переключатель «Уведомления» в профиле ставит/отменяет уникальную periodic work. Тема, уведомления и авто-отдых сохраняются в SharedPreferences и переживают перезапуск приложения.

---

## Дизайн

- Адаптивная иконка: гантель и галочка, foreground в безопасной зоне 108×108, фон `#1A73E8`, Primary `#3B82F6`, отдельный монохромный слой для themed-иконок.
- Material 3 палитра: primary `#3B82F6`, secondary `#10B981` (зелёная кнопка завершения), tertiary `#F59E0B`, error `#EF4444`.
- Скругления карточек 16.dp, elevation 4.dp, жирные заголовки.
- Подходы — анимированные «пилюли» с чекбоксом; отдых — круговой индикатор; пустые состояния — с иконкой и действием.

---

## Скриншоты

Скриншоты можно добавить при передаче проекта. Положите файлы в `docs/screenshots/` и вставьте в этот раздел:

```markdown
![Главный экран](docs/screenshots/home.png)
![Детали тренировки](docs/screenshots/workout-detail.png)
![Профиль](docs/screenshots/profile.png)
```

Быстрый захват с подключённого устройства/эмулятора:

```powershell
adb exec-out screencap -p > docs/screenshots/home.png
```

---

## Проверка перед передачей

```powershell
.\gradlew assembleDebug
.\gradlew test
.\gradlew assembleRelease
```

Дополнительно вручную проверяются запуск на эмуляторе, поворот экрана, длинный список подходов и отображение иконки в лаунчере.

## Конфигурация агента

Правила работы находятся в `AGENT.md` (не коммитится, добавлен в `.gitignore`).
