package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

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
    onWordClick: (String) -> Unit
) {
    // Basic regex to find english words
    val regex = "\\b[a-zA-Z][a-zA-Z0-9'-]*\\b".toRegex()
    val matches = regex.findAll(text)
    
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        for (match in matches) {
            append(text.substring(lastIndex, match.range.first))
            
            val startIndex = this.length
            append(match.value)
            val endIndex = this.length
            
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
        modifier = modifier.pointerInput(text) {
            detectTapGestures { pos ->
                layoutResult.value?.let { result ->
                    val offset = result.getOffsetForPosition(pos)
                    annotatedString.getStringAnnotations(tag = "word", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            onWordClick(annotation.item)
                        }
                }
            }
        },
        style = style,
        onTextLayout = {
            layoutResult.value = it
        }
    )
}
