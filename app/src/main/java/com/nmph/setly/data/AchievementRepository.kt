package com.nmph.setly.data

import com.nmph.setly.data.local.AchievementEntity

/** Категория трофея: определяет цвет фона и иконку по умолчанию. */
enum class TrophyCategory(val iconResName: String, val colorHex: Long) {
    STRENGTH("ic_dumbbell", 0xFFE57373),
    DISCIPLINE("ic_calendar", 0xFF64B5F6),
    PROGRESS("ic_chart", 0xFF81C784),
    TIME("ic_clock", 0xFFFFB74D),
    CLOUD("ic_cloud", 0xFF4DD0E1),
    MASTERY("ic_star", 0xFFBA68C8),
}

/** Справочник из 50 трофеев. Эмодзи остаётся запасным вариантом, если иконка не найдена. */
object AchievementRepository {
    val allAchievements: List<AchievementEntity> = listOf(
        AchievementEntity("first_workout", "Первый шаг", "Создайте первую тренировку", "🌱"),
        AchievementEntity("first_set", "Первый подход", "Отметьте самый первый подход", "✅"),
        AchievementEntity("first_day", "Первый день", "Выполните 100% подходов за день", "☀️"),
        AchievementEntity("first_finish", "Первый успех", "Завершите тренировку полностью", "🏁"),
        AchievementEntity("novice", "Новичок", "Выполните 10 подходов за всё время", "👶"),
        AchievementEntity("organizer", "Организатор", "Добавьте 2 упражнения в одну тренировку", "📋"),
        AchievementEntity("worker", "Трудяга", "Выполните 50 повторений за всё время", "🛠️"),
        AchievementEntity("athlete", "Спортсмен", "Выполните 500 повторений за всё время", "🏃"),
        AchievementEntity("heavy", "Тяжеловес", "Выполните 1000 повторений за всё время", "🏋️"),
        AchievementEntity("titan", "Титан", "Выполните 10000 повторений за всё время", "🗿"),
        AchievementEntity("streak_2", "Не сдавайся", "Тренируйтесь 2 дня подряд", "🚶"),
        AchievementEntity("streak_3", "В ритме", "Тренируйтесь 3 дня подряд", "🏃‍♂️"),
        AchievementEntity("streak_7", "Неделя силы", "Тренируйтесь 7 дней подряд", "📅"),
        AchievementEntity("streak_14", "Две недели", "Тренируйтесь 14 дней подряд", "🗓️"),
        AchievementEntity("streak_30", "Месяц дисциплины", "Тренируйтесь 30 дней подряд", "🏅"),
        AchievementEntity("streak_100", "Железная воля", "Тренируйтесь 100 дней подряд", "👑"),
        AchievementEntity("return", "Возвращение", "Вернитесь к тренировкам после пропуска", "🔄"),
        AchievementEntity("early_bird", "Ранняя пташка", "Выполните подход до 08:00", "🌅"),
        AchievementEntity("night_owl", "Полуночник", "Выполните подход после 22:00", "🌙"),
        AchievementEntity("marathon", "Марафонец", "Выполните 10 тренировок за месяц", "🏆"),
        AchievementEntity("pushup_king", "Король отжиманий", "1000 отжиманий за всё время", "👑"),
        AchievementEntity("squat_master", "Мастер приседаний", "1000 приседаний за всё время", "🦵"),
        AchievementEntity("iron_abs", "Железный пресс", "500 упражнений на пресс", "🥊"),
        AchievementEntity("steel_grip", "Стальной хват", "300 подходов на руки", "✊"),
        AchievementEntity("one_day_500", "Одним днем", "500 повторений за день", "⚡"),
        AchievementEntity("sprinter", "Спринтер", "100 повторений за один подход", "💨"),
        AchievementEntity("endurance", "Выносливый", "Подход длится более 5 минут", "🕐"),
        AchievementEntity("triathlon", "Триатлонист", "Упражнения из 3 категорий за день", "🏊‍♂️"),
        AchievementEntity("sleep_setup", "Слушай тело", "Установите время сна", "😴"),
        AchievementEntity("auto_rest", "Режим", "Тренировка с автоматическим отдыхом", "⏳"),
        AchievementEntity("perfect_timing", "Идеальный тайминг", "Подход ровно до конца таймера", "🎯"),
        AchievementEntity("patient", "Терпеливый", "Подход после 5 минут отдыха", "🧘"),
        AchievementEntity("circles", "Круг за кругом", "10 кругов за тренировку", "🔄"),
        AchievementEntity("planner", "Планировщик", "Создайте тренировку на 30 дней", "📝"),
        AchievementEntity("long_term", "Долгосрочная цель", "Тренировка на 90 дней", "🎯"),
        AchievementEntity("no_skip", "Без пропусков", "Все подходы за день без пропусков", "🚫"),
        AchievementEntity("dynamic", "Динамика", "Измените количество повторений", "📈"),
        AchievementEntity("adapt", "Адаптация", "Измените время тренировки", "⏱️"),
        AchievementEntity("night_warrior", "Ночной воин", "Тренировка между 00:00 и 04:00", "🦉"),
        AchievementEntity("sync", "Синхронизация", "Успешная синхронизация с Google", "☁️"),
        AchievementEntity("cloud", "Облачный", "Восстановление из облака", "🌥️"),
        AchievementEntity("expert", "Эксперт", "Создайте 10 упражнений", "🧠"),
        AchievementEntity("collector", "Коллекционер", "Упражнения из всех категорий", "🎒"),
        AchievementEntity("calendar", "Календарь", "Посмотрите статистику за прошлый месяц", "📅"),
        AchievementEntity("perfectionist", "Перфекционист", "100% подходов в тренировке", "💯"),
        AchievementEntity("editor", "Редактор", "Измените название тренировки", "✏️"),
        AchievementEntity("notified", "Уведомленный", "Откройте push-уведомление", "🔔"),
        AchievementEntity("legend", "Легенда", "100 тренировок за всё время", "⭐"),
        AchievementEntity("first_gym", "Первая тренировка", "Создайте 3 разных упражнения", "🏋️"),
        AchievementEntity("collector_2", "Коллекционер 2", "Соберите 25 трофеев", "🏅"),
    )

    fun byId(id: String): AchievementEntity? = allAchievements.firstOrNull { it.id == id }

    val size: Int get() = allAchievements.size

    /** Категория нужна для цветного фона карточки трофея. */
    fun categoryFor(id: String): TrophyCategory = when (id) {
        "first_workout", "first_set", "first_finish", "novice", "organizer", "worker",
        "athlete", "heavy", "titan", "pushup_king", "squat_master", "iron_abs",
        "steel_grip", "one_day_500", "sprinter", "endurance", "first_gym", "legend"
        -> TrophyCategory.STRENGTH

        "first_day", "streak_2", "streak_3", "streak_7", "streak_14", "streak_30",
        "streak_100", "return", "marathon", "no_skip", "perfectionist", "planner",
        "long_term", "circles"
        -> TrophyCategory.DISCIPLINE

        "early_bird", "night_owl", "night_warrior", "patient", "perfect_timing",
        "auto_rest", "sleep_setup", "adapt"
        -> TrophyCategory.TIME

        "sync", "cloud", "notified"
        -> TrophyCategory.CLOUD

        "dynamic", "expert", "collector", "calendar", "editor", "triathlon",
        "collector_2"
        -> TrophyCategory.MASTERY

        else -> TrophyCategory.PROGRESS
    }

    /** Иконка скачана с Iconify (Material Design Icons); при отсутствии ресурса используется эмодзи. */
    fun iconResName(id: String): String = when (id) {
        "first_set", "no_skip", "perfectionist" -> "ic_check_circle"
        "first_day", "planner", "long_term", "calendar" -> "ic_calendar"
        "streak_2", "streak_3", "streak_7", "streak_14", "streak_30", "streak_100" -> "ic_fire"
        "marathon", "sprinter" -> "ic_run"
        "night_owl", "sleep_setup", "early_bird" -> "ic_sleep"
        "sync", "cloud", "return" -> "ic_cloud"
        "notified" -> "ic_bell"
        "legend", "collector_2", "collector" -> "ic_trophy"
        "expert" -> "ic_account"
        "dynamic", "athlete", "worker" -> "ic_chart"
        "lock" -> "ic_lock"
        else -> categoryFor(id).iconResName
    }
}
