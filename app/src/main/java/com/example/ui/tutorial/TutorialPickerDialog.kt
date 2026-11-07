package com.example.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AppFontState
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppTab
import com.example.ui.theme.Typography as AppTypography
import com.example.ui.theme.forAppLanguage
import com.example.ui.theme.localizedFor
import com.example.ui.theme.withFontFamily

/**
 * Settings ▸ Interactive tutorial. Pick the tutorial language (independent
 * of the app language, remembered), then either one section — the basics of
 * the screen or a single tab, each teaching only itself — or the full tour.
 * The dialog speaks the tutorial language, so switching the chips previews
 * the choice at once.
 */
@Composable
fun TutorialPickerDialog(
    appLanguage: AppLanguage,
    controller: TutorialController,
    tabOrder: List<AppTab>,
    onStart: (List<TutorialTopic>) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = controller.languageFor(appLanguage)
    val context = LocalContext.current
    val res = remember(language, context) { context.applicationContext.localizedFor(language).resources }
    val typography = remember(language) {
        AppTypography.forAppLanguage(language).withFontFamily(
            AppFontState.resolvedAppFamily(fa = language == AppLanguage.FA)
        )
    }
    val direction = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
    val topics = remember(tabOrder) { TutorialTopic.fullTour(tabOrder) }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(typography = typography) {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Filled.School, contentDescription = null) },
                title = {
                    Text(
                        text = res.getString(R.string.tutorial_picker_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = res.getString(R.string.tutorial_picker_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = res.getString(R.string.tutorial_language_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TutorialLanguageChips(
                            language = language,
                            onLanguage = { controller.setLanguage(it) },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = res.getString(R.string.tutorial_pick_section),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        topics.forEach { topic ->
                            TopicRow(
                                title = res.getString(topic.title),
                                description = res.getString(topic.description),
                                topic = topic,
                                onClick = { onStart(listOf(topic)) },
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { onStart(topics) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(res.getString(R.string.tutorial_full_tour))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(res.getString(R.string.tutorial_close)) }
                },
            )
        }
    }
}

@Composable
private fun TopicRow(
    title: String,
    description: String,
    topic: TutorialTopic,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = topic.icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
