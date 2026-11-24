package com.nmph.setly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Глобальное упражнение: категория, единица измерения и иконка хранятся один раз. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String = DEFAULT_CATEGORY,
    val unit: String = DEFAULT_UNIT,
    val iconResName: String = DEFAULT_ICON,
) {
    companion object {
        const val DEFAULT_CATEGORY = "Грудь"
        const val DEFAULT_UNIT = "Повторения"
        const val DEFAULT_ICON = "fitness_center"
    }
}
