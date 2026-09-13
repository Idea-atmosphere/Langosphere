package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.logic.TextDirectionUtils
import com.example.logic.autoTextDirection
import com.example.ui.components.anime.toonOn
import com.example.ui.components.anime.toonSoft
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.isAnimeDesign

@Composable
fun ClickableWordText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    // Color used for the tappable-word highlight/underline. Defaults to the
    // app's accent color, which is what you want in contexts like the
    // subtitle list (a clear "this word is tappable" affordance). Pass the
    // subtitle's own chosen color here to keep the whole line in that color
    // instead of it being overridden by the accent color.
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    underlineWords: Boolean = true,
    onWordClick: (String) -> Unit,
    // Optional callback fired when the text itself (outside any word) is
    // tapped — used to open the sentence's learning lesson. Taps on words
    // always go to onWordClick and never trigger this.
    onTextClick: (() -> Unit)? = null,
    // Word-state decoration used by the toon skin: words the user already
    // knows get a mint wash, words being learned get a sakura wash and a
    // hand-drawn wavy underline. Both default to "unknown", so existing
    // callers are unaffected.
    knownWords: Set<String> = emptySet(),
    learningWords: Set<String> = emptySet(),
) {
    val anime = isAnimeDesign()
    // The word-chip washes, resolved for the current theme: on the night
    // canvas the light tints become deep ones so the text stays readable.
    val knownFill = toonSoft(AnimeColors.MintSoft)
    val learningFill = toonSoft(AnimeColors.SakuraSoft)
    val neutralFill = toonSoft(AnimeColors.SunnySoft)
    // Basic regex to find english words
    val regex = "\\b[a-zA-Z][a-zA-Z0-9'-]*\\b".toRegex()
    val matches = regex.findAll(text)
    
    // Which words carry the toon "learning" state — collected while the
    // string is built so the wavy underline can be painted under exactly
    // those ranges afterwards.
    val learningRanges = mutableListOf<IntRange>()

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        for (match in matches) {
            append(text.substring(lastIndex, match.range.first))
            
            val startIndex = this.length
            append(match.value)
            val endIndex = this.length

            if (anime) {
                // Toon words are inline chips: a soft pastel wash behind the
                // glyphs instead of an underline. Known = mint, learning =
                // sakura (plus the wavy rule drawn below), everything else
                // gets the tappable sunny highlight.
                val lower = match.value.lowercase()
                val isKnown = lower in knownWords
                val isLearning = lower in learningWords
                if (isLearning) learningRanges += startIndex until endIndex
                val chipFill = when {
                    isKnown -> knownFill
                    isLearning -> learningFill
                    else -> neutralFill
                }
                addStyle(
                    style = SpanStyle(
                        color = toonOn(chipFill),
                        background = chipFill,
                    ),
                    start = startIndex,
                    end = endIndex,
                )
                addStringAnnotation(
                    tag = "word",
                    annotation = match.value,
                    start = startIndex,
                    end = endIndex,
                )
                lastIndex = match.range.last + 1
                continue
            }

            addStyle(
                style = SpanStyle(
                    color = highlightColor,
                    textDecoration = if (underlineWords) TextDecoration.Underline else TextDecoration.None
                ),
                start = startIndex,
                end = endIndex
            )
            addStringAnnotation(
                tag = "word",
                annotation = match.value,
                start = startIndex,
                end = endIndex
            )
            
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        modifier = modifier
            .then(
                if (anime) {
                    Modifier.drawBehind {
                        // The hand-drawn wavy rule under "learning" words:
                        // a chain of quadratic curves along the bottom of
                        // each word's measured box.
                        val result = layoutResult.value ?: return@drawBehind
                        val amplitude = 1.6.dp.toPx()
                        val wavelength = 5.dp.toPx()
                        learningRanges.forEach { range ->
                            val box: Rect = runCatching { result.getBoundingBox(range.first) }
                                .getOrNull() ?: return@forEach
                            val end: Rect = runCatching { result.getBoundingBox(range.last) }
                                .getOrNull() ?: box
                            val y = box.bottom - amplitude
                            val left = kotlin.math.min(box.left, end.left)
                            val right = kotlin.math.max(box.right, end.right)
                            if (right <= left) return@forEach
                            val path = Path().apply {
                                moveTo(left, y)
                                var x = left
                                var up = true
                                while (x < right) {
                                    val nextX = kotlin.math.min(x + wavelength, right)
                                    quadraticBezierTo(
                                        (x + nextX) / 2f,
                                        if (up) y - amplitude else y + amplitude,
                                        nextX,
                                        y,
                                    )
                                    up = !up
                                    x = nextX
                                }
                            }
                            drawPath(path, AnimeColors.Sakura, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                } else Modifier
            )
            .pointerInput(text) {
            detectTapGestures { pos ->
                val result = layoutResult.value
                if (result != null) {
                    val offset = result.getOffsetForPosition(pos)
                    val annotation = annotatedString.getStringAnnotations(tag = "word", start = offset, end = offset)
                        .firstOrNull()
                    if (annotation != null) {
                        onWordClick(annotation.item)
                    } else {
                        onTextClick?.invoke()
                    }
                } else {
                    onTextClick?.invoke()
                }
            }
        },
        // Auto RTL/LTR: Persian documents/subtitles render right-to-left,
        // Latin ones left-to-right, independent of the app menu language.
        // M3 Text takes the direction via TextStyle, so it goes on the style.
        // Also auto-align Right for RTL and Left for LTR so English lines
        // stay left even when the app composition is RTL (FA).
        // When caller passes an absolute Left/Right/Center/Justify we preserve
        // it — this lets subtitle EN stay Left and FA stay Right even when
        // the text content would otherwise auto-flip.
        style = run {
            val autoAlign = when (style.textAlign) {
                TextAlign.Center, TextAlign.Justify, TextAlign.Left, TextAlign.Right -> style.textAlign
                else -> if (TextDirectionUtils.isRtl(text)) TextAlign.Right else TextAlign.Left
            }
            style.copy(
                textDirection = text.autoTextDirection(),
                textAlign = autoAlign
            )
        },
        onTextLayout = {
            layoutResult.value = it
        }
    )
}
