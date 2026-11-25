package com.nmph.setly

import com.nmph.setly.data.local.ExerciseEntity
import com.nmph.setly.data.local.PlannedExercise
import com.nmph.setly.data.local.ProgressEntity
import com.nmph.setly.data.local.WorkoutEntity
import com.nmph.setly.utils.DistributionHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DistributionHelperTest {
    private fun plan(dailyTargetReps: Int, repsPerSet: Int = 30) = PlannedExercise(
        exercise = ExerciseEntity(name = "Отжимания", id = 1),
        dailyTargetReps = dailyTargetReps,
        repsPerSet = repsPerSet,
    )

    private fun millis(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun workout(
        startDate: LocalDate = LocalDate.of(2026, 11, 1),
        periodDays: Int = 30,
        createdAt: Long? = null,
    ) = WorkoutEntity(
        name = "Тест",
        startDate = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        createdAt = createdAt
            ?: startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        periodDays = periodDays,
        sleepStart = "22:00",
        sleepEnd = "08:00",
    )

    @Test
    fun `sleep across midnight is measured correctly`() {
        assertEquals(600, DistributionHelper.sleepMinutes("22:00", "08:00"))
        assertEquals(660, DistributionHelper.sleepMinutes("21:00", "08:00"))
    }

    @Test
    fun `sets are defined by daily target and reps per set`() {
        assertEquals(10, DistributionHelper.totalSetsPerDay(300, 30))
        assertEquals(4, DistributionHelper.totalSetsPerDay(100, 30))
        assertEquals(listOf(30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            DistributionHelper.setsForPlan(plan(dailyTargetReps = 300), workout()))
    }

    @Test
    fun `last set can be shorter with ceiling`() {
        assertEquals(
            listOf(30, 30, 30, 30, 13),
            DistributionHelper.setsForDay(dailyTargetReps = 133, repsPerSet = 30),
        )
    }

    @Test
    fun `windows split awake time into equal per-exercise slots`() {
        // 22:00-08:00 = 10 ч сна → 14 ч бодрствования → 840 / 10 = 84 мин на окно.
        val windows = DistributionHelper.calculateWindowsForExercise("22:00", "08:00", 10)
        assertEquals(840, DistributionHelper.awakeMinutes("22:00", "08:00"))
        assertEquals(10, windows.size)
        assertEquals(480, windows.first().startMinute) // 08:00
        assertEquals("08:00-09:24", windows.first().label.replace('–', '-'))
        assertEquals(84, windows.first().durationMinutes)
        assertEquals("20:36", windows.last().startLabel)
        assertEquals(1320, windows.last().endMinute) // 22:00
        assertEquals(840, windows.sumOf { it.durationMinutes })
    }

    @Test
    fun `window crosses midnight when awake period wraps around`() {
        val windows = DistributionHelper.calculateWindowsForExercise("01:00", "09:00", 10)
        assertEquals(10, windows.size)
        assertEquals(540, windows.first().startMinute) // 09:00
        assertEquals(96, windows.first().durationMinutes)
        assertEquals(60, windows.last().endMinute) // 01:00 следующего дня
        assertEquals(true, windows.last().wrapsMidnight)
        assertEquals(960, windows.sumOf { it.durationMinutes })
    }

    @Test
    fun `window remainder is spread without losing minutes`() {
        val windows = DistributionHelper.calculateWindowsForExercise("22:00", "08:00", 11)
        assertEquals(11, windows.size)
        assertEquals(4, windows.count { it.durationMinutes == 77 })
        assertEquals(7, windows.count { it.durationMinutes == 76 })
        assertEquals(840, windows.sumOf { it.durationMinutes })
    }

    @Test
    fun `dynamic rest starts at full awake window for workout created today`() {
        val today = LocalDate.of(2026, 11, 25)
        val createdAt = millis(today, 8)
        val plan = workout(startDate = today, createdAt = createdAt)
        assertEquals(84, DistributionHelper.calculateDynamicRest(plan, 10, 0, createdAt))
    }

    @Test
    fun `dynamic rest is recalculated after quick marks and when time is late`() {
        val today = LocalDate.of(2026, 11, 25)
        val createdAt = millis(today, 8)
        val plan = workout(startDate = today, createdAt = createdAt)

        // Два подхода подряд в 09:00: оставшиеся 780 минут делятся на 8 подходов.
        assertEquals(97, DistributionHelper.calculateDynamicRest(plan, 10, 2, millis(today, 9)))
        // К 12:00 остаётся меньше времени, поэтому интервал сокращается.
        assertEquals(75, DistributionHelper.calculateDynamicRest(plan, 10, 2, millis(today, 12)))
    }

    @Test
    fun `older workout uses eight o'clock anchor and overdue sets are detected`() {
        val today = LocalDate.of(2026, 11, 25)
        val plan = workout(startDate = today.minusDays(1), createdAt = millis(today.minusDays(1), 9))
        val now = millis(today, 20)
        val schedule = DistributionHelper.calculateDynamicSchedule(plan, 10, 0, now)

        assertEquals(12, schedule.restMinutes)
        assertEquals(true, DistributionHelper.isSetOverdue(schedule, 0, false, now))
        assertEquals(false, DistributionHelper.isSetOverdue(schedule, 0, true, now))
    }

    @Test
    fun `dynamic rest is never shorter than one minute`() {
        val today = LocalDate.of(2026, 11, 25)
        val plan = workout(startDate = today, createdAt = millis(today, 8))
        assertEquals(1, DistributionHelper.calculateDynamicRest(plan, 10, 0, millis(today, 21, 55)))
    }

    @Test
    fun `debt is spread over seven days without losing reps`() {
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 1),
            (1..7).map { DistributionHelper.debtShare(7, it) })
        assertEquals(listOf(2, 2, 2, 1, 1, 1, 1),
            (1..7).map { DistributionHelper.debtShare(10, it) })
        assertEquals(10, (1..7).sumOf { DistributionHelper.debtShare(10, it) })
    }

    @Test
    fun `adjusted target includes previous day debt and expires after seven days`() {
        val today = LocalDate.of(2026, 11, 24)
        val yesterday = today.minusDays(1)
        val progress = listOf(
            ProgressEntity(
                id = "${yesterday}_1",
                date = yesterday.toString(),
                exerciseId = 1,
                completedSets = listOf(0, 1),
            ),
        )
        // Норма 300, выполнено 2 подхода по 30 = 60 → долг 240 → +35 в первый день.
        val oneDayWorkout = workout(startDate = yesterday, periodDays = 1)
        assertEquals(
            335,
            DistributionHelper.calculateAdjustedTarget(plan(300), today, progress, oneDayWorkout),
        )
        assertEquals(
            300,
            DistributionHelper.calculateAdjustedTarget(
                plan(300), today.plusDays(7), progress, oneDayWorkout,
            ),
        )
    }
}
