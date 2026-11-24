package com.nmph.setly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nmph.setly.R
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.theme.IconAccent
import com.nmph.setly.ui.viewmodel.TimerPhase
import com.nmph.setly.ui.viewmodel.WorkoutTimerState

/** Универсальное пустое состояние с иконкой, пояснением и действием. */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Block),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(IconAccent),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == Icons.Default.FitnessCenter) {
                Image(
                    painter = painterResource(R.drawable.ic_dumbbell),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White,
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(actionText)
            }
        }
    }
}

/** Подход в виде «таблетки»: нажатие переключает отметку. */
@Composable
fun SetPill(
    reps: Int,
    checked: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val background by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "setPillBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onSecondary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "setPillContent",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) contentColor else Color.Transparent)
                .border(2.dp, contentColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = background,
                )
            }
        }
        Text(
            if (compact) reps.toString() else "$reps раз",
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

/** Круговой отсчёт отдыха с крупным временем по центру. */
@Composable
fun RestTimer(
    timeLeft: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                AppCards.Shape,
            )
            .padding(vertical = AppSpacing.Block),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (totalSeconds > 0) timeLeft.toFloat() / totalSeconds else 0f },
                modifier = Modifier.size(200.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 10.dp,
            )
            Text(
                if (timeLeft > 0) "%02d:%02d".format(timeLeft / 60, timeLeft % 60) else "0:00",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            if (timeLeft > 0) "Отдых" else "Отдых окончен!",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Круговой таймер подхода: синий «Выполнение», оранжевый «Отдых». */
@Composable
fun PhaseTimer(
    state: WorkoutTimerState,
    modifier: Modifier = Modifier,
) {
    val isExecution = state.phase == TimerPhase.EXECUTION
    val phaseColor = if (isExecution) Color(0xFF2196F3) else Color(0xFFFF9800)
    val phaseLabel = if (isExecution) "Выполнение" else "Отдых"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, AppCards.Shape)
            .padding(vertical = AppSpacing.Block),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = {
                    state.timeLeft.toFloat() / state.totalSeconds.coerceAtLeast(1).toFloat()
                },
                modifier = Modifier.size(180.dp),
                color = phaseColor,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeWidth = 10.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(state.timeLeft / 60, state.timeLeft % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    phaseLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = phaseColor,
                )
            }
        }
    }
}
