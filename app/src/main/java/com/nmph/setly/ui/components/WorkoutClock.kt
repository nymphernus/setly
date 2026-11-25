package com.nmph.setly.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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

/** Одно упражнение на циферблате: свой цвет, окна подходов и состояние их выполнения. */
data class ClockSection(
    val exerciseId: Int,
    val label: String,
    val color: Color,
    val windows: List<TimeWindow>,
    /** Индексы окон, соответствующие отмеченным подходам. */
    val completedWindows: Set<Int> = emptySet(),
    /** Окно, которое только что отметили — подсвечивается пульсацией. */
    val highlightedWindow: Int? = null,
)

/** Палитра приложения: терракота, шалфей, чернила, песок и их оттенки. */
private val SECTION_COLORS = listOf(
    Color(0xFFE07A5F), // терракота
    Color(0xFF81B29A), // шалфей
    Color(0xFF3D405B), // чернила
    Color(0xFFC8A165), // песок
    Color(0xFF4C72B0), // синий
    Color(0xFFB07B9C), // дымчато-розовый
    Color(0xFF2E8B8B), // бирюзовый
    Color(0xFFC8553D), // глубокая терракота
)

private val SLEEP_COLOR = Color(0xFF6E6E73)
private const val CLOCK_SIZE_DP = 320

/** Цвет упражнения на часах: стабилен для его позиции в тренировке. */
fun clockColor(index: Int): Color = SECTION_COLORS[index % SECTION_COLORS.size]

/**
 * Аналоговые часы на 24 часа. Каждый час подписан, полчаса отмечены короткой засечкой.
 * При выборе упражнения показывается только его кольцо, увеличенное во всю ширину циферблата.
 */
@Composable
fun WorkoutClock(
    sections: List<ClockSection>,
    sleepStart: String,
    sleepEnd: String,
    selectedExerciseId: Int? = null,
    onSelectExercise: ((Int?) -> Unit)? = null,
    sizeDp: Int = CLOCK_SIZE_DP,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }

    val pulse by rememberInfiniteTransition(label = "clockPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "clockPulseAlpha",
    )

    val focused = sections.firstOrNull { it.exerciseId == selectedExerciseId }
    val visibleSections = if (focused != null) listOf(focused) else sections

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
    val hourTextSize = with(density) { 8.sp.toPx() }
    val hourPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
    ) {
        Box(modifier = Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .then(
                        if (focused != null && onSelectExercise != null) {
                            Modifier.clickable { onSelectExercise(null) }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                // Запас под подписи часов: 26.dp, иначе «23:00» обрезается краем Canvas.
                val outerRadius = size.minDimension / 2 - 26.dp.toPx()
                val band = if (focused != null) {
                    outerRadius * 0.62f
                } else {
                    (outerRadius * 0.70f) / (visibleSections.size + 1).coerceAtLeast(1)
                }

                // Сон: отдельное кольцо в общем виде, тонкая дуга при фокусе на упражнении.
                if (focused == null) {
                    drawCircle(
                        color = trackColor,
                        radius = outerRadius - band / 2,
                        style = Stroke(band),
                    )
                    drawWindowArc(sleepWindow, center, outerRadius - band / 2, band, SLEEP_COLOR)
                } else {
                    drawWindowArc(
                        sleepWindow,
                        center,
                        outerRadius - 4.dp.toPx(),
                        4.dp.toPx(),
                        SLEEP_COLOR.copy(alpha = 0.65f),
                    )
                }

                visibleSections.forEachIndexed { index, section ->
                    val radius = if (focused != null) {
                        outerRadius - band / 2
                    } else {
                        outerRadius - band * (index + 1.5f)
                    }
                    val strokeWidth = if (focused != null) band else band * 0.8f
                    drawCircle(color = trackColor, radius = radius, style = Stroke(strokeWidth))

                    section.windows.forEachIndexed { windowIndex, window ->
                        val isCompleted = windowIndex in section.completedWindows
                        val isHighlighted = section.highlightedWindow == windowIndex
                        val color = when {
                            isHighlighted -> section.color.copy(alpha = pulse)
                            isCompleted -> section.color.copy(alpha = 0.22f)
                            else -> section.color
                        }
                        drawWindowArc(
                            window,
                            center,
                            radius,
                            if (isHighlighted) strokeWidth * 1.35f else strokeWidth,
                            color,
                        )
                    }
                }

                // 24 часа: подпись «00:00» … «23:00» и засечка на каждый час.
                // Полчаса — короткая линия без подписи.
                for (hour in 0..23) {
                    val hourAngle = -90f + hour * 15f
                    drawTick(hourAngle, center, outerRadius, 8.dp.toPx(), 2.dp.toPx(), tickColor)

                    val labelAngle = Math.toRadians(hourAngle.toDouble())
                    val labelRadius = outerRadius + 14.dp.toPx()
                    hourPaint.color = tickColor.toArgb()
                    hourPaint.textSize = hourTextSize
                    hourPaint.textAlign = Paint.Align.CENTER
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            "%02d:00".format(hour),
                            center.x + cos(labelAngle).toFloat() * labelRadius,
                            center.y + sin(labelAngle).toFloat() * labelRadius + hourTextSize / 3,
                            hourPaint,
                        )
                    }
                    drawTick(
                        hourAngle + 7.5f,
                        center,
                        outerRadius,
                        5.dp.toPx(),
                        1.dp.toPx(),
                        tickColor.copy(alpha = 0.6f),
                    )
                }

                val minuteOfDay = now.hour * 60 + now.minute
                val handAngle = Math.toRadians((-90f + minuteOfDay * 0.25f).toDouble())
                drawLine(
                    color = handColor,
                    start = center,
                    end = Offset(
                        center.x + cos(handAngle).toFloat() * outerRadius * 0.62f,
                        center.y + sin(handAngle).toFloat() * outerRadius * 0.62f,
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (focused != null) {
                    Text(
                        focused.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = focused.color,
                    )
                }
                Text(
                    "%02d:%02d".format(now.hour, now.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (focused != null) "нажмите, чтобы вернуться" else "сейчас",
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
            visibleSections.forEach { section ->
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
                    onClick = if (onSelectExercise != null) {
                        {
                            onSelectExercise(
                                if (selectedExerciseId == section.exerciseId) null else section.exerciseId,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    detail: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
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

private fun DrawScope.drawTick(
    angle: Float,
    center: Offset,
    outerRadius: Float,
    length: Float,
    width: Float,
    color: Color,
) {
    val radians = Math.toRadians(angle.toDouble())
    val start = outerRadius + 2.dp.toPx()
    drawLine(
        color = color,
        start = Offset(
            center.x + cos(radians).toFloat() * start,
            center.y + sin(radians).toFloat() * start,
        ),
        end = Offset(
            center.x + cos(radians).toFloat() * (start + length),
            center.y + sin(radians).toFloat() * (start + length),
        ),
        strokeWidth = width,
    )
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
