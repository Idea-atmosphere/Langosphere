package com.example.ui.components.anime

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.AnimeMascotState
import com.example.ui.theme.AppStrings
import kotlin.math.cos
import kotlin.math.sin

/**
 * **Sora** — the toon skin's chibi mascot: pink hair, blue headphones, a
 * yellow star hairpin and a cream hoodie.
 *
 * She is drawn entirely with Compose vector primitives (paths, arcs and
 * circles) rather than shipped as a bitmap. That is deliberate:
 *
 *  • she stays razor sharp at every size, from the 26dp tab-bar avatar to
 *    the 180dp Reader hero, with no density buckets to maintain;
 *  • her ink outline and cream "sticker" frame are taken from the live
 *    color scheme, so she re-inks herself correctly in dark mode instead of
 *    carrying a baked-in cream background that would look cut out;
 *  • the whole mascot costs zero bytes of APK size and zero decode time.
 *
 * If you later prefer illustrated artwork, drop `sora_hero.webp` /
 * `sora_avatar.webp` into `res/drawable` and swap the [SoraFace] call inside
 * [SoraMascot] / [SoraAvatar] for an `Image` — nothing else has to change,
 * because every caller goes through those two composables.
 */

/** Sora's expression, which drives her tint, her scale and how many
 *  sparkles orbit her. */
enum class SoraMood { Idle, Happy, Thinking, Cheer }

private val HairColor = Color(0xFFFF8FBF)
private val HairShade = Color(0xFFE86FA3)
private val SkinColor = Color(0xFFFFE0CC)
private val HoodieColor = Color(0xFFFFF6EC)
private val HeadphoneColor = AnimeColors.Sky

/**
 * The full mascot: Sora inside a cream circular "sticker" frame with a 3dp
 * ink border, bobbing gently and surrounded by sparkles.
 *
 * The sticker frame matters — it is what keeps her from ever looking like a
 * cut-out with a stray background square behind it.
 *
 * Idle motion (bob + sparkle pulse) is skipped entirely when the system has
 * animations turned off ([reduceMotion]) or when [animate] is false, which
 * is how pager pages stop their loops while scrolled off screen.
 */
@Composable
fun SoraMascot(
    size: Dp,
    modifier: Modifier = Modifier,
    mood: SoraMood = SoraMood.Idle,
    animate: Boolean = true,
    sparkles: Boolean = true,
    frame: Boolean = true,
) {
    val still = reduceMotion() || !animate
    val transition = rememberInfiniteTransition(label = "sora")

    val bob by if (still) {
        remember { mutableFloatOf(0f) }
    } else {
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sora-bob",
        )
    }
    val orbit by if (still) {
        remember { mutableFloatOf(0f) }
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sora-orbit",
        )
    }
    val moodScale by animateFloatAsState(
        targetValue = when (mood) {
            SoraMood.Happy, SoraMood.Cheer -> 1.10f
            SoraMood.Thinking -> 0.97f
            SoraMood.Idle -> 1f
        },
        animationSpec = toonPopSpring(),
        label = "sora-mood-scale",
    )

    Box(
        modifier = modifier
            .size(size)
            // Decorative: her greeting text carries the meaning, so the
            // drawing itself must not add noise for screen readers.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = bob * 8.dp.toPx()
                    rotationZ = bob * 2f
                    scaleX = moodScale
                    scaleY = moodScale
                },
        ) {
            if (frame) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .inkShadow(offset = 4.dp, shape = CircleShape)
                        .clip(CircleShape)
                        // Sora keeps her paper-white face at night too: she
                        // is drawn with ink linework, so a dark disc would
                        // erase her. The ink border and hard shadow are what
                        // separate her from the night canvas.
                        .background(AnimeColors.Paper)
                        .inkBorder(3.dp, CircleShape)
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (frame) size * 0.10f else 0.dp),
            ) {
                drawSora(mood = mood, ink = AnimeColors.Ink)
            }
        }

        if (sparkles) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSparkleRing(
                    angleDeg = orbit,
                    count = when (mood) {
                        SoraMood.Cheer -> 6
                        SoraMood.Happy -> 5
                        SoraMood.Thinking -> 2
                        SoraMood.Idle -> 3
                    },
                    pulse = if (still) 1f else 0.6f + 0.55f * ((sin(Math.toRadians(orbit.toDouble() * 3)) + 1) / 2).toFloat(),
                )
            }
        }
    }
}

/**
 * Sora's headshot — the chat avatar, the app-bar badge and the tab-bar
 * "Agent" icon. Static by default (it sits in chrome, where a bobbing head
 * would be distracting) and always framed so it never looks cut out.
 */
@Composable
fun SoraAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
    mood: SoraMood = SoraMood.Idle,
    borderWidth: Dp = 2.dp,
    /** Set false for the assistant's own avatar, which is her identity
     *  rather than decoration and therefore ignores the "Show Sora"
     *  setting. */
    respectMascotSetting: Boolean = true,
) {
    if (respectMascotSetting && !AnimeMascotState.enabled) return

    val scale by animateFloatAsState(
        targetValue = if (mood == SoraMood.Happy || mood == SoraMood.Cheer) 1.10f else 1f,
        animationSpec = toonPopSpring(),
        label = "sora-avatar-scale",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            // Same reasoning as SoraMascot: the face stays paper so the ink
            // drawing on top of it is legible on either canvas.
            .background(AnimeColors.Paper)
            .inkBorder(borderWidth, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.06f)) {
            // NB: inside this DrawScope `size` is the canvas Size, not the
            // Dp parameter — the zoom pivot has to come from the former.
            val canvas = this.size
            // The headshot is the same drawing zoomed onto the head.
            scale(scaleX = 1.5f, scaleY = 1.5f, pivot = Offset(canvas.width / 2f, canvas.height * 0.62f)) {
                drawSora(mood = mood, ink = AnimeColors.Ink, headOnly = true)
            }
        }
    }
}

/**
 * Sora's speech bubble. Just a [ToonBubble] with her voice: bold body copy
 * and a tail that points back at wherever she is standing.
 */
@Composable
fun SoraSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    tail: BubbleTail = BubbleTail.Left,
    tint: Color? = null,
) {
    ToonBubble(modifier = modifier, tail = tail, fill = tint) {
        androidx.compose.material3.Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Sora's rotating greetings, in the app's current UI language. Callers pick
 * one (usually by index, or at random on first composition) — see
 * ReaderScreen's empty state and the assistant header.
 */
fun soraGreetings(strings: AppStrings, leitnerDue: Int = 0): List<String> = listOf(
    strings.soraGreetHello,
    strings.soraGreetTapWords,
    strings.soraGreetLeitner(leitnerDue),
    strings.soraGreetEpisode,
)

// ── Drawing ──

/**
 * Paints the chibi. Coordinates are normalised against the smaller side of
 * the canvas, so the same path data works at 26dp and at 180dp.
 */
private fun DrawScope.drawSora(mood: SoraMood, ink: Color, headOnly: Boolean = false) {
    val s = kotlin.math.min(size.width, size.height)
    val cx = size.width / 2f
    // The head sits in the upper half so there is room for the body below.
    val headCy = if (headOnly) size.height * 0.62f else size.height * 0.38f
    val headR = s * (if (headOnly) 0.30f else 0.26f)
    val line = s * 0.035f
    val stroke = Stroke(width = line)

    if (!headOnly) {
        // ── Body: the cream hoodie ──
        val bodyTop = headCy + headR * 0.72f
        val bodyPath = Path().apply {
            moveTo(cx - s * 0.24f, size.height * 0.95f)
            lineTo(cx - s * 0.21f, bodyTop + s * 0.06f)
            quadraticBezierTo(cx, bodyTop - s * 0.05f, cx + s * 0.21f, bodyTop + s * 0.06f)
            lineTo(cx + s * 0.24f, size.height * 0.95f)
            close()
        }
        drawPath(bodyPath, HoodieColor)
        drawPath(bodyPath, ink, style = stroke)

        // Hoodie pocket seam — a single curve is enough to read as fabric.
        drawPath(
            Path().apply {
                moveTo(cx - s * 0.13f, size.height * 0.82f)
                quadraticBezierTo(cx, size.height * 0.88f, cx + s * 0.13f, size.height * 0.82f)
            },
            ink.copy(alpha = 0.55f),
            style = Stroke(width = line * 0.7f),
        )

        // ── The glowing dictionary she is holding ──
        val bookW = s * 0.30f
        val bookH = s * 0.20f
        val bookLeft = cx - bookW / 2f
        val bookTop = size.height * 0.72f
        drawRoundRect(
            color = AnimeColors.Sunny.copy(alpha = 0.35f),
            topLeft = Offset(bookLeft - s * 0.03f, bookTop - s * 0.03f),
            size = Size(bookW + s * 0.06f, bookH + s * 0.06f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f),
        )
        drawRoundRect(
            color = AnimeColors.Lavender,
            topLeft = Offset(bookLeft, bookTop),
            size = Size(bookW, bookH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(bookLeft, bookTop),
            size = Size(bookW, bookH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f),
            style = stroke,
        )
        drawLine(
            color = ink,
            start = Offset(cx, bookTop),
            end = Offset(cx, bookTop + bookH),
            strokeWidth = line * 0.8f,
        )

        // ── The waving arm ──
        val waveAngle = if (mood == SoraMood.Cheer) -38f else -22f
        rotate(degrees = waveAngle, pivot = Offset(cx + s * 0.20f, bodyTop + s * 0.10f)) {
            drawLine(
                color = HoodieColor,
                start = Offset(cx + s * 0.20f, bodyTop + s * 0.10f),
                end = Offset(cx + s * 0.36f, bodyTop - s * 0.06f),
                strokeWidth = line * 3.2f,
            )
            drawCircle(SkinColor, radius = s * 0.055f, center = Offset(cx + s * 0.37f, bodyTop - s * 0.07f))
            drawCircle(ink, radius = s * 0.055f, center = Offset(cx + s * 0.37f, bodyTop - s * 0.07f), style = stroke)
        }
    }

    // ── Hair: the back layer, a wide rounded mass behind the face ──
    drawCircle(HairShade, radius = headR * 1.16f, center = Offset(cx, headCy - headR * 0.04f))

    // ── Face ──
    drawCircle(SkinColor, radius = headR, center = Offset(cx, headCy))
    drawCircle(ink, radius = headR, center = Offset(cx, headCy), style = stroke)

    // ── Hair: the front fringe, drawn as three overlapping tufts ──
    val fringe = Path().apply {
        moveTo(cx - headR * 1.02f, headCy - headR * 0.10f)
        quadraticBezierTo(cx - headR * 0.95f, headCy - headR * 1.20f, cx, headCy - headR * 1.05f)
        quadraticBezierTo(cx + headR * 0.95f, headCy - headR * 1.20f, cx + headR * 1.02f, headCy - headR * 0.10f)
        quadraticBezierTo(cx + headR * 0.72f, headCy - headR * 0.42f, cx + headR * 0.34f, headCy - headR * 0.22f)
        quadraticBezierTo(cx, headCy - headR * 0.62f, cx - headR * 0.34f, headCy - headR * 0.22f)
        quadraticBezierTo(cx - headR * 0.72f, headCy - headR * 0.42f, cx - headR * 1.02f, headCy - headR * 0.10f)
        close()
    }
    drawPath(fringe, HairColor)
    drawPath(fringe, ink, style = stroke)

    // ── Blue headphones ──
    val bandRect = Rect(
        left = cx - headR * 1.22f,
        top = headCy - headR * 1.30f,
        right = cx + headR * 1.22f,
        bottom = headCy + headR * 1.14f,
    )
    drawArc(
        color = HeadphoneColor,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(bandRect.left, bandRect.top),
        size = Size(bandRect.width, bandRect.height),
        style = Stroke(width = line * 2.4f),
    )
    // The two ear cups.
    listOf(-1f, 1f).forEach { side ->
        val ex = cx + side * headR * 1.02f
        val ey = headCy + headR * 0.06f
        drawRoundRect(
            color = HeadphoneColor,
            topLeft = Offset(ex - headR * 0.20f, ey - headR * 0.26f),
            size = Size(headR * 0.40f, headR * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(headR * 0.16f),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(ex - headR * 0.20f, ey - headR * 0.26f),
            size = Size(headR * 0.40f, headR * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(headR * 0.16f),
            style = stroke,
        )
    }

    // ── Yellow star hairpin ──
    translate(left = cx - headR * 0.74f, top = headCy - headR * 0.70f) {
        drawStar(radius = headR * 0.26f, color = AnimeColors.Sunny, ink = ink, strokeWidth = line * 0.8f)
    }

    // ── Face features. The eyes carry the mood. ──
    val eyeDx = headR * 0.38f
    val eyeCy = headCy + headR * 0.10f
    val eyeR = headR * 0.15f
    when (mood) {
        SoraMood.Happy, SoraMood.Cheer -> {
            // Happy ^ ^ eyes.
            listOf(-1f, 1f).forEach { side ->
                drawPath(
                    Path().apply {
                        moveTo(cx + side * eyeDx - eyeR, eyeCy + eyeR * 0.4f)
                        quadraticBezierTo(cx + side * eyeDx, eyeCy - eyeR * 0.9f, cx + side * eyeDx + eyeR, eyeCy + eyeR * 0.4f)
                    },
                    ink,
                    style = Stroke(width = line * 1.1f),
                )
            }
        }
        else -> {
            listOf(-1f, 1f).forEach { side ->
                drawCircle(ink, radius = eyeR, center = Offset(cx + side * eyeDx, eyeCy))
                // The catchlight is what makes an anime eye read as an eye.
                drawCircle(
                    Color.White,
                    radius = eyeR * 0.38f,
                    center = Offset(cx + side * eyeDx - eyeR * 0.28f, eyeCy - eyeR * 0.32f),
                )
            }
        }
    }
    // Blush.
    listOf(-1f, 1f).forEach { side ->
        drawCircle(
            AnimeColors.Sakura.copy(alpha = 0.45f),
            radius = headR * 0.13f,
            center = Offset(cx + side * headR * 0.62f, eyeCy + headR * 0.26f),
        )
    }
    // Mouth.
    drawPath(
        Path().apply {
            val my = eyeCy + headR * 0.40f
            moveTo(cx - headR * 0.13f, my)
            when (mood) {
                SoraMood.Thinking -> lineTo(cx + headR * 0.13f, my)
                else -> quadraticBezierTo(cx, my + headR * 0.20f, cx + headR * 0.13f, my)
            }
        },
        ink,
        style = Stroke(width = line * 1.0f),
    )
}

/** A four-point manga sparkle. */
private fun DrawScope.drawStar(
    radius: Float,
    color: Color,
    ink: Color,
    strokeWidth: Float,
    waist: Float = 0.32f,
) {
    val path = Path()
    for (i in 0 until 8) {
        val angle = Math.toRadians((i * 45f).toDouble())
        val r = if (i % 2 == 0) radius else radius * waist
        val x = (r * cos(angle)).toFloat()
        val y = (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
    if (strokeWidth > 0f) drawPath(path, ink, style = Stroke(width = strokeWidth))
}

/** The sparkles orbiting the mascot, in the palette's three highlight hues. */
private fun DrawScope.drawSparkleRing(angleDeg: Float, count: Int, pulse: Float) {
    if (count <= 0) return
    val s = kotlin.math.min(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val orbitR = s * 0.47f
    val colors = listOf(AnimeColors.Sunny, AnimeColors.Sakura, AnimeColors.Sky)
    for (i in 0 until count) {
        val angle = Math.toRadians((angleDeg + i * (360f / count)).toDouble())
        val x = cx + (orbitR * cos(angle)).toFloat()
        val y = cy + (orbitR * sin(angle)).toFloat()
        // Each sparkle pulses on its own beat so the ring never looks
        // mechanical.
        val localPulse = pulse * (0.75f + 0.25f * ((i % 3) / 2f))
        translate(left = x, top = y) {
            rotate(degrees = angleDeg * 1.6f + i * 30f, pivot = Offset.Zero) {
                drawStar(
                    radius = s * 0.055f * localPulse,
                    color = colors[i % colors.size],
                    ink = AnimeColors.Ink,
                    strokeWidth = 0f,
                )
            }
        }
    }
}

/** Small float state helper used to keep the reduce-motion branches
 *  structurally identical to the animated ones. */
private fun mutableFloatOf(value: Float) = androidx.compose.runtime.mutableFloatStateOf(value)
