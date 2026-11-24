# Setly

**Setly** — offline-first трекер тренировок для Android. Приложение разбивает дневную норму упражнений на подходы, показывает их временные окна на аналоговых часах, ведёт историю и трофеи, а по желанию синхронизирует данные с Firebase.

Версия: **1.0.0** (`versionCode = 1`) — первый релиз.

---

## Возможности

- Создание тренировок и упражнений, связь «многие ко многим»: одно упражнение используется в разных тренировках со своими целями.
- Дневная норма и подходы: `ceil(цель / число дней)`, разбиение на подходы по `repsPerSet` (например, `43 → 20, 20, 3`).
- Временные окна для каждого упражнения отдельно: время бодрствования делится на число его подходов, с учётом перехода сна через полночь.
- Аналоговые часы на 24 часа: у каждого упражнения свой цвет и своё кольцо секторов, сон выделен тёмно-серым, в центре — текущее цифровое время.
- Независимая отметка каждого подхода «пилюлями»-чекбоксами. Таймеров и общих чекбоксов кругов (supersets) нет: нажатие просто фиксирует факт выполнения.
- Система долгов: невыполненная норма переносится на 7 дней вперёд и распределяется по ним без потери повторений.
- Календарь истории с цветовой индикацией прогресса и ручным вводом фактических повторений за день.
- 40 трофеев в трёх разделах: «Сила» (15), «Дисциплина» (15), «Особые» (10); разворачиваемый список и Snackbar при разблокировке.
- Облако: слияние по ID при обычной синхронизации, создание бекапа, список бекапов с датой/временем и восстановление с перезаписью локальных данных.
- Профиль: Google-аккаунт, статистика, статус «Последняя синхронизация», кнопки «Бекап», «Восстановить», «Синхронизировать (слияние)», настройки темы и уведомлений.
- Нижняя навигация: «Тренировки», «Упражнения», «История», «Трофеи», «Профиль».
- Тёмная тема включена по умолчанию; пустые состояния с иконкой и действием.

---

## Стек технологий

| Область | Решение |
| --- | --- |
| Язык | Kotlin 2.x, JVM target 17 |
| UI | Jetpack Compose, Material 3, Navigation Compose, `Canvas` для часов |
| Локальная БД | Room (схема v14), KSP, Flow |
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
.\gradlew assembleDebug       # отладочный APK
.\gradlew testDebugUnitTest   # unit-тесты
.\gradlew assembleRelease     # релизный APK
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

Приложение полностью работоспособно без аккаунта и без сети: источник истины — локальная Room-БД `AppDatabase` (схема v14). Google Sign-In опционален и используется только для облачных операций.

Основные сущности:

- `WorkoutEntity` — тренировка (`periodDays`, `sleepStart`/`sleepEnd`, `startDate`, `notificationsEnabled`, `remindBeforeMinutes`).
- `ExerciseEntity` — упражнение (`name`, `category`, `unit`, `iconResName`).
- `WorkoutExerciseCrossRef` — связь тренировка ↔ упражнение с полями `dailyTargetReps` и `repsPerSet`.
- `ProgressEntity` — выполненные подходы на дату (`id = "YYYY-MM-DD_<exerciseId>"`, `completedSets` — 0-based индексы, хранятся CSV-строкой через `Converters`).
- `AchievementEntity` — справочник трофеев и состояние их разблокировки.

Миграции 1→14 сохраняют пользовательские данные: v13 удаляет 15 устаревших трофеев, v14 обновляет описания двух трофеев под временные окна.

### Синхронизация

`data/sync/SyncManager.kt` работает с путями:

- `users/{uid}/workouts/{workoutId}` и `.../exercises/{exerciseId}`
- `users/{uid}/progress/{YYYY-MM-DD_exerciseId}`
- `users/{uid}/backups/{timestamp}`

Обычная синхронизация — слияние по ID: запись, которой нет в другой стороне, копируется туда; при конфликте выбирается более полная версия. Для прогресса это большее число `completedSets`, затем более поздняя `date`; для тренировок — более поздняя `startDate`, затем больший `periodDays`.

Бекап сохраняет упражнения, тренировки, связи и прогресс в отдельный документ с таймстемпом. Список бекапов показывает дату и время, восстановление полностью перезаписывает локальные данные. Любая операция с облаком ограничена 10 секундами; время последней синхронизации и ошибки показываются в профиле.

### Распределение и временные окна

`utils/DistributionHelper.kt` — единый источник расчётов:

- `totalSetsPerDay` / `setsForDay` — число подходов и их размеры (`50 / 20 → [20, 20, 10]`).
- `calculateWindowsForExercise(sleepStart, sleepEnd, totalSets)` — список `TimeWindow` в минутах от полуночи. Для сна 22:00–08:00 и 10 подходов это 10 окон по 84 минуты с 08:00 до 22:00; если период бодрствования переходит через полночь, окно помечается `wrapsMidnight`.
- `debtShare` и `calculateAdjustedTarget` — перенесённый долг за предыдущие 7 дней (долг 10 → `2, 2, 2, 1, 1, 1, 1`).
- `completedRepsForDate` — фактические повторения по индексам отмеченных подходов.

Таймеров и расчёта отдыха больше нет: время отображается только как секторы на часах.

### Навигация и экраны

`MainActivity` содержит внешний `NavHost`: `splash` → `home_root`, `workout/{workoutId}`, `edit_workout/{workoutId}`, `exercise/{exerciseId}`, `day_detail/{date}`. Внутри `MainScreen` — нижняя навигация `home`, `exercises`, `history`, `achievements`, `profile`; `innerPadding` `Scaffold` пробрасывается в `NavHost`, поэтому контент не перекрывается панелью.

`WorkoutDetailScreen` показывает вверху `WorkoutClock` с секторами всех упражнений, ниже — отдельный блок на каждое упражнение с заголовком «Отжимания (Цель: 300)» и списком чипсов-чекбоксов. `TodayViewModel` формирует список активных на сегодня тренировок и подходов; отметка подхода сразу пишет `ProgressEntity` и запускает проверку трофеев.

### Уведомления и настройки

`NotificationWorker` через WorkManager показывает напоминание каждые 30 минут. Переключатель «Уведомления» в профиле ставит/отменяет уникальную periodic work. Тема и уведомления сохраняются в SharedPreferences и переживают перезапуск приложения; тёмная тема — значение по умолчанию.

---

## Дизайн

- Адаптивная иконка: гантель и галочка, foreground в безопасной зоне 108×108, фон `#1A73E8`, Primary `#3B82F6`, отдельный монохромный слой для themed-иконок.
- Material 3 палитра: primary `#3B82F6`, secondary `#10B981` (зелёная кнопка завершения), tertiary `#F59E0B`, error `#EF4444`.
- Скругления карточек 16.dp, elevation 2.dp, жирные заголовки.
- Подходы — анимированные «пилюли» с чекбоксом; часы — концентрические цветные сектора; пустые состояния — с иконкой и действием.

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
.\gradlew testDebugUnitTest
.\gradlew assembleRelease
```

Дополнительно вручную проверяются запуск на эмуляторе, поворот экрана, секторы часов и независимые блоки упражнений в деталях тренировки, ввод фактических данных в календаре, группировка трофеев, бекап/восстановление/слияние и отображение иконки в лаунчере.
