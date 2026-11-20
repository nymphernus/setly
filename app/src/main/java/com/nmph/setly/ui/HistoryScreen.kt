package com.nmph.setly.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.ui.viewmodel.HistoryViewModel
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val progress by viewModel.dayProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val monthTitle = month.month.getDisplayName(TextStyle.FULL, Locale("ru"))
        .replaceFirstChar { it.uppercase(Locale("ru")) } + " " + month.year

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { viewModel.prevMonth() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Предыдущий месяц")
            }
            Text(monthTitle, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.nextMonth() }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Следующий месяц")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(7) { index ->
                val label = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[index]
                Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            // Смещение: java.time недели начинаются с понедельника (1 = Пн).
            items(month.atDay(1).dayOfWeek.value - 1) {
                Box(Modifier.aspectRatio(1f))
            }
            items(month.lengthOfMonth()) { index ->
                val date = month.atDay(index + 1)
                val ratio = progress[date]
                val color = when {
                    ratio == null || ratio == 0f -> Color(0xFFE0E0E0)
                    ratio < 0.5f -> Color(0xFFFFCDD2)
                    ratio < 1f -> Color(0xFFFFF59D)
                    else -> Color(0xFFA5D6A7)
                }
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                        .border(1.dp, Color.LightGray, androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            val text = if (ratio == null) {
                                "${date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))}: нет данных"
                            } else {
                                "${date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))}: ${(ratio * 100).toInt()}%"
                            }
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(date.dayOfMonth.toString())
                }
            }
        }
    }
}
