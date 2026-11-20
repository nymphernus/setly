package com.nmph.setly.data.local

import androidx.room.TypeConverter

// Храним выполненные подходы как CSV ("20,20,10"), чтобы не тащить JSON-библиотеку.
class Converters {
    @TypeConverter
    fun fromCompletedSets(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(',').mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter
    fun toCompletedSets(sets: List<Int>): String = sets.joinToString(",")
}
