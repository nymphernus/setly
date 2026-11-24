package com.nmph.setly.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.utils.DistributionHelper
import com.nmph.setly.utils.TimeWindow
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/** Одно упражнение на циферблате: свой цвет и свои независимые окна подходов. */
data class ClockSection(
    val exerciseId: Int,
    val label: String,
    val color: Color,
    val windows: List<TimeWindow>,
)

private val SECTION_COLORS = listOf(
    Color(0xFF4CAF50), // зелёный
    Color(0xFF2196F3), // синий
    Color(0xFFFF9800), // оранжевый
    Color(0xFF9C27B0), // фиолетовый
    Color(0xFF00BCD4), // голубой
    Color(0xFFE91E63), // розовый
    Color(0xFF8BC34A), // лаймовый
    Color(0xFFFF5722), // красный
)

private val SLEEP_COLOR = Color(0xFF424242)
private const val CLOCK_SIZE_DP = 300

/** Цвет упражнения на часах: стабилен для его позиции в тренировке. */
fun clockColor(index: Int): Color = SECTION_COLORS[index % SECTION_COLORS.size]

/**
 * Аналоговые часы на 24 часа. Каждое упражнение рисуется отдельным кольцом со своим цветом,
 * сон — тёмно-серым сектором. В центре — текущее цифровое время.
 */
@Composable
fun WorkoutClock(
    sections: List<ClockSection>,
    sleepStart: String,
    sleepEnd: String,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }

    val sleepStartMinute = DistributionHelper.minutesOfDay(sleepStart)
    val sleepLength = DistributionHelper.sleepMinutes(sleepStart, sleepEnd)
    val sleepRawEnd = sleepStartMinute + sleepLength
    val sleepEndMinute = if (sleepRawEnd == DistributionHelper.MINUTES_PER_DAY) {
        DistributionHelper.MINUTES_PER_DAY
    } else {
        sleepRawEnd % DistributionHelper.MINUTES_PER_DAY
    }
    val sleepWindow = TimeWindow(sleepStartMinute, sleepEndMinute)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val handColor = MaterialTheme.colorScheme.primary

    val density = LocalDensity.current
    val tickTextSize = with(density) { 10.sp.toPx() }
    val handWidth = with(density) { 2.dp.toPx() }
    val tickPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
    ) {
        Box(modifier = Modifier.size(CLOCK_SIZE_DP.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(CLOCK_SIZE_DP.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val outerRadius = size.minDimension / 2 - 12.dp.toPx()
                val band = (outerRadius * 0.72f) / (sections.size + 1).coerceAtLeast(1)

                drawCircle(color = trackColor, radius = outerRadius, style = Stroke(band))

                // Сон — отдельный тёмно-серый сектор на внешнем кольце.
                drawWindowArc(sleepWindow, center, outerRadius - band / 2, band, SLEEP_COLOR)

                sections.forEachIndexed { index, section ->
                    val radius = outerRadius - band * (index + 1.5f)
                    drawCircle(color = trackColor, radius = radius, style = Stroke(band * 0.8f))
                    section.windows.forEach { window ->
                        drawWindowArc(window, center, radius, band * 0.8f, section.color)
                    }
                }

                // Часовые отметки и подписи: 24 часа, метки каждые 3 часа.
                for (hour in 0..23) {
                    val angle = -90f + hour * 15f
                    val radians = Math.toRadians(angle.toDouble())
                    val isMajor = hour % 3 == 0
                    val tickStart = outerRadius + 2.dp.toPx()
                    val tickEnd = tickStart + if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                    drawLine(
                        color = tickColor,
                        start = Offset(
                            center.x + cos(radians).toFloat() * tickStart,
                            center.y + sin(radians).toFloat() * tickStart,
                        ),
                        end = Offset(
                            center.x + cos(radians).toFloat() * tickEnd,
                            center.y + sin(radians).toFloat() * tickEnd,
                        ),
                        strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    )
                    if (isMajor) {
                        val labelRadius = tickEnd + tickTextSize
                        tickPaint.color = tickColor.toArgb()
                        tickPaint.textSize = tickTextSize
                        tickPaint.textAlign = Paint.Align.CENTER
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "%02d".format(hour),
                                center.x + cos(radians).toFloat() * labelRadius,
                                center.y + sin(radians).toFloat() * labelRadius + tickTextSize / 3,
                                tickPaint,
                            )
                        }
                    }
                }

                // Тонкая стрелка на текущее время.
                val minuteOfDay = now.hour * 60 + now.minute
                val handAngle = Math.toRadians((-90f + minuteOfDay * 0.25f).toDouble())
                drawLine(
                    color = handColor,
                    start = center,
                    end = Offset(
                        center.x + cos(handAngle).toFloat() * outerRadius * 0.62f,
                        center.y + sin(handAngle).toFloat() * outerRadius * 0.62f,
                    ),
                    strokeWidth = handWidth,
                    cap = StrokeCap.Round,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(now.hour, now.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "сейчас",
                    style = MaterialTheme.typography.labelSmall,
                    color = tickColor,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner / 2),
        ) {
            LegendRow(color = SLEEP_COLOR, label = "Сон", detail = sleepWindow.label)
            sections.forEach { section ->
                val first = section.windows.firstOrNull()
                LegendRow(
                    color = section.color,
                    label = section.label,
                    detail = if (first == null) {
                        "нет окон"
                    } else {
                        "${section.windows.size} × ${first.durationMinutes} мин · " +
                            "${first.startLabel}–${section.windows.last().endLabel}"
                    },
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawWindowArc(
    window: TimeWindow,
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color,
) {
    val day = DistributionHelper.MINUTES_PER_DAY
    val arcSize = Size(radius * 2, radius * 2)
    val topLeft = Offset(center.x - radius, center.y - radius)

    fun segment(startMinute: Int, length: Int) {
        if (length <= 0) return
        drawArc(
            color = color,
            startAngle = -90f + startMinute * 360f / day,
            sweepAngle = length * 360f / day,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(strokeWidth),
        )
    }

    if (window.wrapsMidnight) {
        segment(window.startMinute, day - window.startMinute)
        segment(0, window.endMinute)
    } else {
        segment(window.startMinute, window.durationMinutes)
    }
}
