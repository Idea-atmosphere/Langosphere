package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One action inside [PlayerToolCluster].
 */
data class PlayerToolAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val active: Boolean = false
)

/**
 * A single button that unfolds into its siblings.
 *
 * The player had grown a row of seven or eight chrome buttons, which is a wall
 * of icons over someone's film. Everything except the essentials now lives
 * behind one button: tapping it slides the rest out with a staggered spring,
 * tapping again folds them back.
 *
 * Deliberately built from [animateFloatAsState] rather than AnimatedVisibility,
 * because this cluster is placed inside boxes nested in columns where the
 * scoped AnimatedVisibility overloads cannot resolve.
 */
@Composable
fun PlayerToolCluster(
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<PlayerToolAction>,
    toggleDescription: String,
    modifier: Modifier = Modifier,
    buttonSize: Int = 38
) {
    // Read the animation through .value rather than a delegate: the delegate
    // form needs the runtime getValue import and is easy to break again.
    val progressState = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "toolClusterProgress"
    )
    val progress = progressState.value

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (progress > 0.01f && actions.isNotEmpty()) {
            // Each action remains a standalone circle. There is deliberately
            // no shared pill/scrim behind the row and no animated width that
            // could squeeze a circle into an oval while opening or closing.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // The last action is closest to the toggle, so it appears
                // first; only opacity/scale animates, never the circle's size.
                actions.forEachIndexed { index, action ->
                    val delay = 0.09f * (actions.lastIndex - index)
                    val span = (1f - delay).coerceAtLeast(0.25f)
                    val itemProgress = ((progress - delay) / span).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .size(buttonSize.dp)
                            .graphicsLayer {
                                alpha = itemProgress
                                scaleX = 0.72f + 0.28f * itemProgress
                                scaleY = 0.72f + 0.28f * itemProgress
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerGlassButton(
                            icon = action.icon,
                            contentDescription = action.contentDescription,
                            onClick = action.onClick,
                            size = buttonSize.dp,
                            active = action.active
                        )
                    }
                }
            }
        }

        PlayerGlassButton(
            icon = if (expanded) Icons.Filled.Close else Icons.Filled.MoreVert,
            contentDescription = toggleDescription,
            onClick = onToggle,
            size = (buttonSize + 2).dp,
            active = expanded
        )
    }
}
