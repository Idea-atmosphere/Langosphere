package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.isNeobrutalismDesign

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
 * Deliberately built from [animateFloatAsState] and per-item width instead of
 * AnimatedVisibility, because this cluster is placed inside boxes nested in
 * columns where the scoped AnimatedVisibility overloads cannot resolve.
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
            val neo = isNeobrutalismDesign()
            Row(
                modifier = Modifier
                    .clip(if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp))
                    .background(
                        if (neo) Color.Transparent
                        else Color.Black.copy(alpha = 0.38f * progress)
                    )
                    .padding(horizontal = 3.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // The last action is closest to the toggle, so it should be the
                // first one to appear; the stagger reads as one gesture.
                actions.forEachIndexed { index, action ->
                    val delay = 0.09f * (actions.lastIndex - index)
                    val span = (1f - delay).coerceAtLeast(0.25f)
                    val itemProgress = ((progress - delay) / span).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width((buttonSize * itemProgress).dp)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = itemProgress
                                scaleX = 0.6f + 0.4f * itemProgress
                                scaleY = 0.6f + 0.4f * itemProgress
                            }
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
