package com.nmph.setly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmph.setly.data.AchievementRepository
import com.nmph.setly.data.TrophySection
import com.nmph.setly.data.local.AchievementEntity
import com.nmph.setly.ui.theme.AppCards
import com.nmph.setly.ui.theme.AppSpacing
import com.nmph.setly.ui.viewmodel.AchievementsViewModel

/** Вкладка с коллекцией трофеев, сгруппированной по разделам. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel,
    modifier: Modifier = Modifier,
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val unlocked = achievements.count { it.isUnlocked && AchievementRepository.byId(it.id) != null }
    var expanded by remember { mutableStateOf(setOf(TrophySection.STRENGTH)) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Трофеи",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            "Открыто $unlocked из ${AchievementRepository.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
            contentPadding = PaddingValues(AppSpacing.Block),
        ) {
            items(AchievementRepository.sections, key = { it.name }) { section ->
                val sectionIds = AchievementRepository.achievementsFor(section).map { it.id }.toSet()
                val sectionAchievements = sectionIds.mapNotNull { id ->
                    achievements.firstOrNull { it.id == id }
                }
                val sectionUnlocked = sectionAchievements.count { it.isUnlocked }
                val isExpanded = section in expanded

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = AppCards.Shape,
                    elevation = CardDefaults.cardElevation(defaultElevation = AppCards.Elevation),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = if (isExpanded) expanded - section else expanded + section
                                }
                                .padding(AppSpacing.CardInner),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(section.colorHex), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                val iconRes = remember(section.iconResName) {
                                    runCatching {
                                        com.nmph.setly.R.drawable::class.java
                                            .getField(section.iconResName).getInt(null)
                                    }.getOrNull()
                                }
                                if (iconRes != null) {
                                    Image(
                                        painter = painterResource(iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    section.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    "$sectionUnlocked из ${sectionIds.size} открыто",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacing.CardInner)
                                    .padding(bottom = AppSpacing.CardInner),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
                            ) {
                                sectionAchievements.forEach { achievement ->
                                    AchievementCard(achievement, section)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementEntity, section: TrophySection) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (achievement.isUnlocked) 1f else 0.55f),
        shape = AppCards.Shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        val iconName = AchievementRepository.iconResName(achievement.id)
        val iconRes = remember(iconName) {
            runCatching {
                com.nmph.setly.R.drawable::class.java.getField(iconName).getInt(null)
            }.getOrNull()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.CardInner),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(section.colorHex), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Text(text = achievement.emoji, fontSize = 28.sp)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AppSpacing.CardInner),
            ) {
                Text(
                    achievement.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!achievement.isUnlocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Не открыто",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
