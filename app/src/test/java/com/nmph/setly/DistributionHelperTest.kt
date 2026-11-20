package com.nmph.setly

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
import com.nmph.setly.utils.DistributionHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionHelperTest {
    private fun exercise(repsPerSet: Int = 30) = ExerciseEntity(
        name = "Отжимания",
        repsPerSet = repsPerSet,
    )

    private fun plan(dailyTargetReps: Int, repsPerSet: Int = 30) = PlannedExercise(
        exercise = exercise(repsPerSet),
        dailyTargetReps = dailyTargetReps,
    )

    @Test
    fun `sleep across midnight is measured correctly`() {
        assertEquals(600, DistributionHelper.sleepMinutes("22:00", "08:00"))
        assertEquals(660, DistributionHelper.sleepMinutes("21:00", "08:00"))
    }

    @Test
    fun `daily target is split into equal sets`() {
        val sets = DistributionHelper.setsForPlan(plan(dailyTargetReps = 300, repsPerSet = 30))
        assertEquals(10, sets.size)
        assertEquals(List(10) { 30 }, sets)
    }

    @Test
    fun `last set can be shorter`() {
        assertEquals(
            listOf(20, 20, 3),
            DistributionHelper.setsForDay(dailyTargetReps = 43, repsPerSet = 20),
        )
    }

    @Test
    fun `rest uses awake time and is capped at 120 minutes`() {
        // 22:00-08:00 = 10 ч сна → 14 ч бодрствования → 840 / 10 = 84 мин.
        assertEquals(84, DistributionHelper.restMinutes("22:00", "08:00", 10))
        assertEquals(120, DistributionHelper.restMinutes("22:00", "08:00", 1))
        assertEquals(120, DistributionHelper.restMinutes("23:59", "00:00", 10))
    }

    @Test
    fun `rest is never less than one minute`() {
        assertEquals(1, DistributionHelper.restMinutes("22:00", "08:00", 1_000))
    }
}
