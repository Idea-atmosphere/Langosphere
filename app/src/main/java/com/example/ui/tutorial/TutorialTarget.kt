package com.example.ui.tutorial

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize

/**
 * Marks a control as something the interactive tutorial can point at.
 *
 * The bounds are recorded in window coordinates — the pager scales and
 * offsets its pages, and the Online/Video tabs swap layout directions, so
 * local coordinates would not be comparable — and the overlay converts them
 * back into its own space. Two rectangles are kept: the visible one (clipped
 * by every scrolling ancestor; empty when the control is scrolled away, which
 * is how a step knows that its control is not on the screen right now) and
 * the unclipped layout one, which lets a host scroll the control into view.
 * The key is removed again when the control leaves the composition (another
 * tab, a collapsed panel, a closed card).
 *
 * Controls inside a Dialog window record bounds of that window; the overlay
 * placed inside the same dialog (see [TutorialHost]) reads them unchanged.
 */
fun Modifier.tutorialTarget(key: String): Modifier = composed {
    val tutorial = LocalTutorial.current
    DisposableEffect(tutorial, key) {
        onDispose { tutorial.targets.remove(key) }
    }
    onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            tutorial.targets.set(
                key = key,
                visible = coordinates.boundsInWindow(),
                layout = Rect(coordinates.positionInWindow(), coordinates.size.toSize()),
            )
        }
    }
}
