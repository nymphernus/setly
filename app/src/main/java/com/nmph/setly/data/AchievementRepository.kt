package com.nmph.setly.data

import com.nmph.setly.data.local.AchievementEntity

/** Раздел коллекции: определяет заголовок аккордеона, цвет и иконку по умолчанию. */
enum class TrophySection(val title: String, val iconResName: String, val colorHex: Long) {
    STRENGTH("Сила", "ic_dumbbell", 0xFFE57373),
    DISCIPLINE("Дисциплина", "ic_calendar", 0xFF64B5F6),
    SPECIAL("Особые", "ic_star", 0xFFBA68C8),
}

private data class TrophyDef(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
)

private fun trophy(id: String, title: String, description: String, emoji: String) =
    TrophyDef(id, title, description, emoji)

private fun List<TrophyDef>.toEntities(): List<AchievementEntity> = map {
    AchievementEntity(it.id, it.title, it.description, it.emoji)
}

/**
 * 40 значимых трофеев: 15 «Сила», 15 «Дисциплина», 10 «Особые».
 * Эмодзи остаётся запасным вариантом, если иконка не найдена.
 */
object AchievementRepository {
    val sections: List<TrophySection> = TrophySection.entries

    private val bySection: Map<TrophySection, List<AchievementEntity>> = mapOf(
        // Сила — 15: серии, объёмы и рекорды.
        TrophySection.STRENGTH to listOf(
            trophy("streak_2", "Не сдавайся", "Тренируйтесь 2 дня подряд", "🚶"),
            trophy("streak_3", "В ритме", "Тренируйтесь 3 дня подряд", "🏃‍♂️"),
            trophy("streak_7", "Неделя силы", "Тренируйтесь 7 дней подряд", "📅"),
            trophy("streak_14", "Две недели", "Тренируйтесь 14 дней подряд", "🗓️"),
            trophy("streak_30", "Месяц дисциплины", "Тренируйтесь 30 дней подряд", "🏅"),
            trophy("streak_100", "Железная воля", "Тренируйтесь 100 дней подряд", "👑"),
            trophy("pushup_king", "Король отжиманий", "1000 отжиманий за всё время", "👑"),
            trophy("squat_master", "Мастер приседаний", "1000 приседаний за всё время", "🦵"),
            trophy("iron_abs", "Железный пресс", "500 повторений на пресс", "🥊"),
            trophy("steel_grip", "Стальной хват", "300 подходов на руки", "✊"),
            trophy("one_day_500", "Одним днём", "500 повторений за день", "⚡"),
            trophy("sprinter", "Спринтер", "100 повторений за один подход", "💨"),
            trophy("endurance", "Выносливый", "Подход длиннее 5 минут", "🕐"),
            trophy("volume_5000", "Пять тысяч", "5000 повторений за всё время", "🔥"),
            trophy("volume_25000", "Гора повторений", "25 000 повторений за всё время", "🗿"),
        ).toEntities(),

        // Дисциплина — 15: регулярность, планирование, ранние и поздние тренировки.
        TrophySection.DISCIPLINE to listOf(
            trophy("streak_180", "Полгода подряд", "Тренируйтесь 180 дней без пропуска", "🌄"),
            trophy("return", "Возвращение", "Вернитесь к тренировкам после пропуска", "🔄"),
            trophy("early_bird", "Ранняя пташка", "Выполните подход до 08:00", "🌅"),
            trophy("night_owl", "Полуночник", "Выполните подход после 22:00", "🌙"),
            trophy("night_warrior", "Ночной воин", "Тренируйтесь между 00:00 и 04:00", "🦉"),
            trophy("marathon", "Марафонец", "10 тренировок за месяц", "🏆"),
            trophy("perfect_timing", "Идеальный тайминг", "Окна подходов распределены равномерно", "🎯"),
            trophy("patient", "Терпеливый", "Окно подхода длиннее 5 минут", "🧘"),
            trophy("circles", "Круг за кругом", "10 кругов в одной тренировке", "🔄"),
            trophy("planner", "Планировщик", "Тренировка на 30 дней", "📝"),
            trophy("long_term", "Долгосрочная цель", "Тренировка на 90 дней", "🎯"),
            trophy("no_skip", "Без пропусков", "Все подходы за день без пропусков", "🚫"),
            trophy("dynamic", "Динамика", "Измените количество повторений", "📈"),
            trophy("adapt", "Адаптация", "Измените время сна", "⏱️"),
            trophy("calendar", "Календарь", "Откройте статистику прошлого месяца", "📅"),
        ).toEntities(),

        // Особые — 10: наборы категорий, облако и секреты.
        TrophySection.SPECIAL to listOf(
            trophy("triathlon", "Триатлонист", "Упражнения из 3 категорий за день", "🏊‍♂️"),
            trophy("collector", "Коллекционер", "Упражнения из всех категорий", "🎒"),
            trophy("expert", "Эксперт", "Создайте 10 упражнений", "🧠"),
            trophy("legend", "Легенда", "100 тренировок за всё время", "⭐"),
            trophy("sync", "Синхронизация", "Выполните слияние с облаком", "☁️"),
            trophy("merge_master", "Мастер слияния", "Объедините локальные и облачные данные", "🧬"),
            trophy("backup_master", "Хранитель данных", "Создайте бекап", "💾"),
            trophy("cloud", "Облачный", "Восстановитесь из бекапа", "🌥️"),
            trophy("notified", "Уведомлённый", "Откройте push-уведомление", "🔔"),
            trophy("collector_2", "Коллекционер 2", "Соберите 25 трофеев", "🏅"),
        ).toEntities(),
    )

    val allAchievements: List<AchievementEntity> = sections.flatMap { bySection.getValue(it) }

    fun achievementsFor(section: TrophySection): List<AchievementEntity> =
        bySection.getValue(section)

    fun byId(id: String): AchievementEntity? = allAchievements.firstOrNull { it.id == id }

    val size: Int get() = allAchievements.size

    fun sectionFor(id: String): TrophySection = bySection.entries
        .firstOrNull { (_, list) -> list.any { it.id == id } }
        ?.key
        ?: TrophySection.SPECIAL

    /** Иконка скачана с Iconify (Material Design Icons); при отсутствии ресурса используется эмодзи. */
    fun iconResName(id: String): String = when (id) {
        "streak_2", "streak_3", "streak_7", "streak_14", "streak_30", "streak_100",
        "streak_180", "volume_5000", "one_day_500" -> "ic_fire"

        "no_skip", "perfect_timing", "backup_master" -> "ic_check_circle"
        "planner", "long_term", "calendar" -> "ic_calendar"
        "marathon", "sprinter", "early_bird", "triathlon" -> "ic_run"
        "night_owl", "night_warrior" -> "ic_sleep"
        "sync", "cloud", "merge_master" -> "ic_cloud"
        "notified" -> "ic_bell"
        "legend", "collector_2", "collector" -> "ic_trophy"
        "expert" -> "ic_account"
        "dynamic", "iron_abs" -> "ic_chart"
        "pushup_king", "squat_master", "steel_grip", "volume_25000" -> "ic_dumbbell"
        "endurance", "patient" -> "ic_clock"
        else -> sectionFor(id).iconResName
    }
}
