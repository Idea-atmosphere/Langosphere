package com.example.ui.tutorial

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Marks a control as something the interactive tutorial can point at.
 *
 * The bounds are recorded in window coordinates — the pager scales and
 * offsets its pages, and the Online/Video tabs swap layout directions, so
 * local coordinates would not be comparable — and the overlay converts them
 * back into its own space. The key is removed again when the control leaves
 * the composition (another tab, a collapsed panel, a closed card), which is
 * how a step knows that its control is not on the screen right now.
 */
fun Modifier.tutorialTarget(key: String): Modifier = composed {
    val tutorial = LocalTutorial.current
    DisposableEffect(tutorial, key) {
        onDispose { tutorial.targets.remove(key) }
    }
    onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) tutorial.targets.set(key, coordinates.boundsInWindow())
    }
}
