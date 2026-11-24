package com.nmph.setly.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nmph.setly.data.local.ExerciseEntity

/** Вариант иконки упражнения; ключ сохраняется в ExerciseEntity.iconResName. */
data class ExerciseIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/** Справочник категорий, единиц измерения и иконок упражнений. */
object ExerciseCatalog {
    val Categories = listOf("Грудь", "Спина", "Ноги", "Руки", "Пресс", "Кардио")
    val Units = listOf("Повторения", "Секунды", "Минуты")

    val IconOptions = listOf(
        ExerciseIconOption("fitness_center", "Гантель", Icons.Default.FitnessCenter),
        ExerciseIconOption("directions_run", "Бег", Icons.Default.DirectionsRun),
        ExerciseIconOption("directions_bike", "Велосипед", Icons.Default.DirectionsBike),
        ExerciseIconOption("self_improvement", "Йога", Icons.Default.SelfImprovement),
        ExerciseIconOption("sports_martial_arts", "Единоборства", Icons.Default.SportsMartialArts),
        ExerciseIconOption("accessible", "Силовая", Icons.Default.Accessible),
        ExerciseIconOption("sports_baseball", "Бейсбол", Icons.Default.SportsBaseball),
        ExerciseIconOption("sports_basketball", "Баскетбол", Icons.Default.SportsBasketball),
        ExerciseIconOption("sports_football", "Футбол", Icons.Default.SportsFootball),
        ExerciseIconOption("sports_soccer", "Соккер", Icons.Default.SportsSoccer),
        ExerciseIconOption("sports_tennis", "Теннис", Icons.Default.SportsTennis),
        ExerciseIconOption("pool", "Плавание", Icons.Default.Pool),
        ExerciseIconOption("hiking", "Ходьба", Icons.Default.Hiking),
        ExerciseIconOption("emoji_events", "Достижения", Icons.Default.EmojiEvents),
    )

    fun iconFor(key: String): ImageVector =
        IconOptions.firstOrNull { it.key == key }?.icon ?: Icons.Default.FitnessCenter

    fun labelFor(key: String): String =
        IconOptions.firstOrNull { it.key == key }?.label.orEmpty()

    fun defaultCategory(): String =
        Categories.firstOrNull { it == ExerciseEntity.DEFAULT_CATEGORY } ?: Categories.first()

    fun defaultUnit(): String =
        Units.firstOrNull { it == ExerciseEntity.DEFAULT_UNIT } ?: Units.first()

    fun defaultIcon(): String =
        IconOptions.firstOrNull { it.key == ExerciseEntity.DEFAULT_ICON }?.key
            ?: IconOptions.first().key
}

/** Общие поля создания/редактирования упражнения без placeholder-текстов. */
@Composable
fun ExerciseEditorFields(
    name: String,
    onNameChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    iconResName: String,
    onIconChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Название") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        DropdownField(
            label = "Категория",
            value = category,
            options = ExerciseCatalog.Categories,
            onSelect = onCategoryChange,
        )
        DropdownField(
            label = "Единица измерения",
            value = unit,
            options = ExerciseCatalog.Units,
            onSelect = onUnitChange,
        )
        Text(
            "Иконка",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseCatalog.IconOptions.forEach { option ->
                val selected = option.key == iconResName
                IconToggleButton(
                    checked = selected,
                    onCheckedChange = { checked -> if (checked) onIconChange(option.key) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.label,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Открыть список",
                    )
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
