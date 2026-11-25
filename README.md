# Setly

**Setly** — умный трекер тренировок для Android. Приложение само считает дневную норму, разбивает её на подходы, подсказывает, когда их делать, и показывает пропущенное. Работает офлайн: аккаунт и интернет нужны только для облачного бекапа.

Версия 1.0.0 (`versionCode = 1`).

## Что умеет

- **Офлайн-first.** Все данные хранятся в локальной базе Room. Приложение работает без регистрации и сети.
- **Тренировки и упражнения.** Связь «многие ко многим»: одно упражнение можно использовать в нескольких тренировках со своими целями.
- **Динамические подходы.** Норма делится на подходы по размеру подхода; оставшееся время дня перераспределяется между неотмеченными подходами после каждой отметки. Пропущенные подходы подсвечиваются красным, но не исчезают.
- **Часы и расписание.** Главный элемент тренировки — большие 24-часовые часы с подписью каждого часа и засечками на полчаса. У каждого упражнения свой цвет и свои дуги, сон выделен серым. Нажатие на упражнение или подход увеличивает его сегменты; повторное нажатие или системная «Назад» возвращает общий вид. Время показывают только часы — рядом с чипсами его нет.
- **Долги.** Невыполненная норма плавно переносится на следующие семь дней.
- **История.** Календарь по месяцам с цветовой индикацией прогресса и ручным вводом фактических повторений.
- **Трофеи.** 40 достижений в разделах «Сила», «Дисциплина» и «Особые».
- **Облако (по желанию).** Google Sign-In, слияние данных с Firestore, бекапы и восстановление.
- **Настройки.** Тёмная тема по умолчанию и periodic-напоминания через WorkManager.

## Требования

- Android Studio **2026.2.2 (Rabbit 2)** или новее.
- **JDK 17** (в комплекте с Android Studio — `jbr`).
- Android SDK: API **37**, Build Tools **36.0.0**. Минимальная версия Android — **10 (API 29)**.
- Аккаунт Firebase — только если нужна синхронизация. Для сборки конфиг Firebase всё равно нужен (плагин Google Services проверяет его на этапе сборки).

## Установка

### Клонирование

```bash
git clone <URL-репозитория> Setly
cd Setly
```

Откройте папку в Android Studio и дождитесь синхронизации Gradle. Оболочка Gradle уже лежит в репозитории — отдельно ставить Gradle не нужно.

### Настройка Firebase

**Проект не содержит `google-services.json` по соображениям безопасности.** Без него сборка не пройдёт: плагин Google Services проверяет конфиг на этапе сборки.

1. Создайте проект в [Firebase Console](https://console.firebase.google.com/).
2. Добавьте Android-приложение с package name `com.nmph.setly`.
3. Скачайте `google-services.json` и положите его в папку `app/` (итоговый путь — `app/google-services.json`). Это обязательный шаг для локальной сборки.
4. Включите **Authentication → Sign-in method → Google** и создайте **Cloud Firestore**.
5. Добавьте SHA-1 отпечаток вашего ключа в настройках Android-приложения Firebase. Быстрый способ получить отпечаток — `./gradlew signingReport` (в Windows: `.\\gradlew signingReport`) и взять значение `SHA1` для нужного варианта `debug` или `release`. Для debug-ключа также подойдёт:

   **Windows (PowerShell):**
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```

   **macOS / Linux:**
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

   Для собственного release-ключа используйте путь и alias вашего keystore. Без SHA-1 Google Sign-In не запустится.
6. Скопируйте **Web Client ID** (из Firebase → Authentication → Sign-in method → Google) в `app/src/main/res/values/strings.xml`, в строку `google_web_client_id`. Это не Android Client ID из `google-services.json`.
7. Настройте правила Firestore так, чтобы пользователь видел только свои данные:

   ```text
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```

Файл `app/google-services.json` намеренно не лежит в репозитории. Как шаблон используйте `app/google-services.example.json`: скопируйте его в `app/google-services.json` и замените заглушки реальными значениями из Firebase.

## Сборка

**Windows (PowerShell):**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew assembleDebug
.\gradlew assembleRelease
.\gradlew testDebugUnitTest
```

**macOS / Linux:**

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

Результаты:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

### Подпись релиза

Сейчас `signingConfigs.release` в `app/build.gradle.kts` использует стандартный `~/.android/debug.keystore` только для тестовой установки. Перед публикацией замените его на собственный keystore и пароли. Файлы `*.jks`, `*.keystore` и `key.properties` игнорируются Git — не коммитьте их.

## Структура проекта

```text
app/src/main/java/com/nmph/setly/
├── MainActivity.kt              # точки входа и навигация
├── data/
│   ├── local/                   # Room: сущности, DAO, БД и миграции
│   ├── sync/                    # Firestore: слияние, бекапы, восстановление
│   ├── auth/                    # Google Sign-In
│   └── settings/                # SharedPreferences (тема, уведомления)
├── ui/                          # экраны и Compose-компоненты
│   ├── components/              # часы, чипсы подходов, пустые состояния
│   └── viewmodel/               # StateFlow и логика экранов
└── utils/                       # расчёт подходов, окон, динамического времени, трофеи
```

Основные расчёты живут в `utils/DistributionHelper.kt`: разбиение нормы на подходы, временные окна, динамический интервал между подходами и перенос долгов. Схема базы — в `data/local/AppDatabase.kt` (текущая версия 15). Версии зависимостей — в `gradle/libs.versions.toml`.

## Где что найти в приложении

- **Тренировки** — планы и отметки на сегодня.
- **Упражнения** — общий список со своим CRUD.
- **История** — календарь и ввод фактических повторений.
- **Трофеи** — достижения по разделам.
- **Профиль** — аккаунт, статистика, бекапы, тема и уведомления.

## Скриншоты

Пока заглушки. Положите реальные изображения в `docs/screenshots/` и замените пути:

| Экран | Файл |
| --- | --- |
| Тренировки | `docs/screenshots/home.png` |
| Детали тренировки | `docs/screenshots/workout-detail.png` |
| Часы в тренировке | `docs/screenshots/workout-clock.png` |
| Профиль | `docs/screenshots/profile.png` |

Быстрый захват с подключённого устройства:

```bash
adb exec-out screencap -p > docs/screenshots/home.png
```
