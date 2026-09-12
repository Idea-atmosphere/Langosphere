package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppStrings

/** One collapsible chapter of the in-app guide. */
private data class GuideTopic(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val points: List<String>
)

/**
 * Settings ▸ "App guide": a complete walkthrough of Langosphere, with one
 * separate chapter per tab and per section, so a new user never has to guess
 * what a button does. Only one chapter is open at a time to keep it readable.
 */
@Composable
fun AppGuideDialog(
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val topics = remember(strings) { buildGuideTopics(strings) }
    var openTitle by remember(strings) { mutableStateOf(topics.first().title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = strings.guideTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.guideSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topics.forEach { topic ->
                    GuideTopicCard(
                        topic = topic,
                        expanded = openTitle == topic.title,
                        onToggle = {
                            openTitle = if (openTitle == topic.title) "" else topic.title
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
private fun GuideTopicCard(
    topic: GuideTopic,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "guideChevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (expanded) scheme.primary.copy(alpha = 0.10f)
                else scheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .clickable(onClick = onToggle)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = if (expanded) 0.20f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    topic.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.primary
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevron },
                tint = scheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                topic.points.forEach { point ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = scheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The guide topics. Titles, summaries and point lists live in XML
 * (guide_* strings/arrays) and are read through [AppStrings].
 */
private fun buildGuideTopics(strings: AppStrings): List<GuideTopic> = listOf(
    GuideTopic(
        icon = Icons.Filled.Check,
        title = strings.guideQuickStartTitle,
        summary = strings.guideQuickStartSummary,
        points = strings.guideQuickStartPoints
    ),
    GuideTopic(
        icon = Icons.Outlined.MenuBook,
        title = strings.guideReaderTabTitle,
        summary = strings.guideReaderTabSummary,
        points = strings.guideReaderTabPoints
    ),
    GuideTopic(
        icon = Icons.Filled.PlayArrow,
        title = strings.guideVideoTabTitle,
        summary = strings.guideVideoTabSummary,
        points = strings.guideVideoTabPoints
    ),
    GuideTopic(
        icon = Icons.Filled.Subtitles,
        title = strings.guideJsonPackageTitle,
        summary = strings.guideJsonPackageSummary,
        points = strings.guideJsonPackagePoints
    ),
    GuideTopic(
        icon = Icons.Filled.Style,
        title = strings.guidePopupsTitle,
        summary = strings.guidePopupsSummary,
        points = strings.guidePopupsPoints
    ),
    GuideTopic(
        icon = Icons.Filled.Language,
        title = strings.guideAiTabTitle,
        summary = strings.guideAiTabSummary,
        points = strings.guideAiTabPoints
    ),
    GuideTopic(
        icon = Icons.Filled.Style,
        title = strings.guideToolsTabTitle,
        summary = strings.guideToolsTabSummary,
        points = strings.guideToolsTabPoints
    ),
    GuideTopic(
        icon = Icons.Filled.Settings,
        title = strings.guideSettingsMenuTitle,
        summary = strings.guideSettingsMenuSummary,
        points = strings.guideSettingsMenuPoints
    ),
    GuideTopic(
        icon = Icons.Filled.Info,
        title = strings.guideTipsTitle,
        summary = strings.guideTipsSummary,
        points = strings.guideTipsPoints
    )
)
