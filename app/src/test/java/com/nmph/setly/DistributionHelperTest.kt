package com.nmph.setly

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.utils.DistributionHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionHelperTest {
    private fun plan(dailyTargetReps: Int, repsPerSet: Int = 30) = PlannedExercise(
        exercise = ExerciseEntity(name = "Отжимания"),
        dailyTargetReps = dailyTargetReps,
        repsPerSet = repsPerSet,
    )

    private fun workout(availableTimeMinutes: Int = 60) = WorkoutEntity(
        name = "Тест",
        startDate = 0L,
        periodDays = 30,
        sleepStart = "22:00",
        sleepEnd = "08:00",
        availableTimeMinutes = availableTimeMinutes,
    )

    @Test
    fun `sleep across midnight is measured correctly`() {
        assertEquals(600, DistributionHelper.sleepMinutes("22:00", "08:00"))
        assertEquals(660, DistributionHelper.sleepMinutes("21:00", "08:00"))
    }

    @Test
    fun `sets are limited by available time`() {
        // rest=10 мин (огранчение планирования), один подход ~2 мин → 60 / 12 = 5 подходов.
        val sets = DistributionHelper.setsForPlan(plan(dailyTargetReps = 300), workout())
        assertEquals(5, sets.size)
        assertEquals(List(5) { 60 }, sets)
    }

    @Test
    fun `max sets is never less than one`() {
        assertEquals(1, DistributionHelper.maxSetsForTime(1, 10, 20))
        assertEquals(listOf(300), DistributionHelper.setsForPlan(plan(300), workout(1)))
    }

    @Test
    fun `last set can be shorter with ceiling`() {
        assertEquals(
            listOf(9, 9, 9, 9, 7),
            DistributionHelper.splitEvenly(dailyTargetReps = 43, maxSets = 5),
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
