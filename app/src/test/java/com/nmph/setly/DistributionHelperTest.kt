package com.nmph.setly

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.utils.DistributionHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionHelperTest {
    private fun workout(
        periodDays: Int,
        autoRestTime: Boolean = true,
        restTimeMinutes: Int = 30,
    ) = WorkoutEntity(
        name = "Тест",
        startDate = 0L,
        periodDays = periodDays,
        sleepStart = "21:00",
        sleepEnd = "08:00",
        restTimeMinutes = restTimeMinutes,
        autoRestTime = autoRestTime,
    )

    private fun exercise(targetReps: Int, repsPerSet: Int = 20) = ExerciseEntity(
        name = "Отжимания",
        targetReps = targetReps,
        repsPerSet = repsPerSet,
    )

    @Test
    fun `daily reps are rounded up`() {
        assertEquals(10, DistributionHelper.dailyReps(workout(30), exercise(300)))
        assertEquals(43, DistributionHelper.dailyReps(workout(7), exercise(300)))
    }

    @Test
    fun `daily reps are split by reps per set`() {
        val exercise = exercise(300, 20)
        assertEquals(listOf(10), DistributionHelper.getSetsForExercise(exercise, 10))
        assertEquals(listOf(20, 20, 3), DistributionHelper.getSetsForExercise(exercise, 43))
    }

    @Test
    fun `rest is based on set count and capped at 120 minutes`() {
        assertEquals(96, DistributionHelper.effectiveRestMinutes(workout(30), 15))
        assertEquals(120, DistributionHelper.effectiveRestMinutes(workout(30), 1))
        assertEquals(30, DistributionHelper.effectiveRestMinutes(workout(30), 0))
    }

    @Test
    fun `manual rest is used when auto rest is off`() {
        assertEquals(45, DistributionHelper.effectiveRestMinutes(workout(30, false, 45), 15))
        assertEquals(
            45,
            DistributionHelper.effectiveRestMinutes(workout(30, true, 45), 15, autoRestEnabled = false),
        )
    }

    @Test
    fun `manual rest above the limit is capped`() {
        assertEquals(120, DistributionHelper.effectiveRestMinutes(workout(30, false, 200), 15))
    }
}
