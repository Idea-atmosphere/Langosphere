package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One row of the reader's kebab (⋮) menu.
 *
 * @param text the row label, already localised by the caller.
 * @param onClick what the row does; the menu is dismissed first.
 * @param checked null for a plain action, otherwise the on/off state of a
 *   toggle, shown as a trailing check mark.
 * @param destructive shown in the error colour (delete actions).
 */
data class KebabItem(
    val text: String,
    val onClick: () -> Unit,
    val checked: Boolean? = null,
    val destructive: Boolean = false,
    /** Optional leading glyph for discoverable settings such as bubble view. */
    val leadingIcon: ImageVector? = null,
    /** Optional live state text shown before the check mark. */
    val status: String? = null,
)

/**
 * The reader's overflow menu: a single ⋮ button holding every secondary
 * action, so the top bars stay minimal.
 *
 * The menu closes on an outside tap ([DropdownMenu.onDismissRequest]) and
 * after every selection. Toggle rows carry their own state as a check mark,
 * so the current choice is readable without opening anything else.
 */
@Composable
fun ReaderKebabMenu(
    items: List<KebabItem>,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String = "گزینه‌ها",
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        items.forEach { item ->
            val leadingIcon = item.leadingIcon
            DropdownMenuItem(
                text = {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        },
                    )
                },
                onClick = {
                    expanded = false
                    item.onClick()
                },
                leadingIcon = if (leadingIcon == null) {
                    null
                } else {
                    {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                },
                trailingIcon = if (item.status != null || item.checked == true) {
                    {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item.status?.let { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.checked == true) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (item.checked == true) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Bilingual labels for the rows the reader offers in its kebab menu.
 *
 * Kept beside the menu rather than in the string tables: the book reader's
 * other chrome (import statuses, toasts) is inline Persian too, and these rows
 * are built from live state (current lesson style, current weight) that a
 * static table cannot express.
 */
class ReaderKebabLabels(private val fa: Boolean) {

    val menuCd = if (fa) "گزینه‌ها" else "Options"
    val openFile = if (fa) "باز کردن فایل (PDF / EPUB)" else "Open a file (PDF / EPUB)"
    val extractRange = if (fa) "انتخاب بازهٔ صفحه و کپی متن" else "Extract a page range"
    val smartCopy = if (fa) "کپی ۵ صفحهٔ بعد برای هوش مصنوعی" else "Copy the next 5 pages for the AI"
    val copyPrompt = if (fa) "کپی پرامپت هوش مصنوعی" else "Copy the AI prompt"
    val promptCopied = if (fa) "پرامپت هوش مصنوعی کپی شد" else "AI prompt copied"
    val displaySettings = if (fa) "تنظیمات نمایش" else "Display settings"
    val bubbleView = if (fa) "نمایش حبابی جملات" else "Sentence bubble view"
    fun bubbleStatus(enabled: Boolean): String =
        if (fa) {
            if (enabled) "فعال" else "غیرفعال"
        } else {
            if (enabled) "On" else "Off"
        }
    val challenge = if (fa) "حالت مطالعه محو / چالش" else "Blur / challenge study mode"
    val chapters = if (fa) "فهرست فصل‌ها" else "Chapters"
    val showPageImage = if (fa) "نمایش تصویر صفحه (عکس‌ها)" else "Show page image"
    val showText = if (fa) "نمایش متن قابل لمس" else "Show tappable text"
    val deleteJson = if (fa) "حذف فایل JSON وارد شده" else "Delete the imported JSON"

    /** «نمایش درس: درون متن» — names the style that is active now. */
    fun lessonDisplay(current: String): String =
        if (fa) "نمایش درس: $current" else "Lesson display: $current"

    /** The three stops of the text-weight switch, in rotation order. */
    fun weightName(index: Int): String = when (index) {
        1 -> if (fa) "متوسط" else "Medium"
        2 -> if (fa) "ضخیم" else "Bold"
        else -> if (fa) "عادی" else "Regular"
    }

    /** «ضخامت متن: متوسط» — tapping it steps to the next stop. */
    fun weightRow(index: Int): String =
        if (fa) "ضخامت متن: ${weightName(index)}" else "Text weight: ${weightName(index)}"
}
