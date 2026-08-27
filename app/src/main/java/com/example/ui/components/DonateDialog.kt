package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.AppStrings
import androidx.compose.material3.HorizontalDivider

/**
 * Donate popup (shown on every app launch, unless dismissed permanently) and
 * the "About" dialog (reachable from the top bar menu). Both share the same
 * donate list (support link + Bitcoin/Tether/TON addresses) and are fully
 * localized via [AppStrings] so they follow the user's chosen app language.
 *
 * The crypto rows use vector drawables (ic_donate_btc / ic_donate_usdt /
 * ic_donate_ton) with a distinct glyph per currency; update the addresses in
 * DonateInfo.kt with your real wallet addresses.
 */

private data class DonateEntry(
    val title: String,
    val subtitle: String,
    val actionValue: String,
    val isLink: Boolean,
    val backgroundColor: Color,
    val contentColor: Color,
    val drawableRes: Int
)

private fun donateEntries(strings: AppStrings): List<DonateEntry> = listOf(
    DonateEntry(
        title = DonateInfo.SUPPORT_LINK_TITLE,
        subtitle = DonateInfo.SUPPORT_LINK_URL,
        actionValue = DonateInfo.SUPPORT_LINK_URL,
        isLink = true,
        backgroundColor = Color(0xFFE2604B),
        contentColor = Color.White,
        drawableRes = R.drawable.ic_donate_link
    ),
    DonateEntry(
        title = strings.bitcoinTitle,
        subtitle = DonateInfo.BTC_ADDRESS,
        actionValue = DonateInfo.BTC_ADDRESS,
        isLink = false,
        backgroundColor = Color(0xFFE8A64A),
        contentColor = Color(0xFF2B1A00),
        drawableRes = R.drawable.ic_donate_btc
    ),
    DonateEntry(
        title = strings.tetherTitle,
        subtitle = DonateInfo.USDT_ADDRESS,
        actionValue = DonateInfo.USDT_ADDRESS,
        isLink = false,
        backgroundColor = Color(0xFF4FA08D),
        contentColor = Color(0xFF04211B),
        drawableRes = R.drawable.ic_donate_usdt
    ),
    DonateEntry(
        title = strings.tonTitle,
        subtitle = DonateInfo.TON_ADDRESS,
        actionValue = DonateInfo.TON_ADDRESS,
        isLink = false,
        backgroundColor = Color(0xFF3B92E6),
        contentColor = Color(0xFF001A33),
        drawableRes = R.drawable.ic_donate_ton
    )
)

@Composable
private fun InfoBanner(strings: AppStrings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            strings.donateInfoBannerText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DonateRow(entry: DonateEntry, strings: AppStrings) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(entry.backgroundColor)
            .clickable {
                if (entry.isLink) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.actionValue)))
                    } catch (e: Exception) {
                        Toast.makeText(context, strings.donateLinkInvalidError, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    clipboardManager.setText(AnnotatedString(entry.actionValue))
                    Toast.makeText(context, strings.addressCopiedToast(entry.title), Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(entry.contentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = entry.drawableRes),
                contentDescription = entry.title,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = entry.contentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(
                entry.subtitle,
                color = entry.contentColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (entry.isLink) Icons.Outlined.Link else Icons.Outlined.ContentCopy,
            contentDescription = if (entry.isLink) strings.openLinkCd else strings.copyAddressCd,
            tint = entry.contentColor
        )
    }
}

@Composable
private fun DonateContent(strings: AppStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBanner(strings)
        donateEntries(strings).forEach { entry -> DonateRow(entry, strings) }
    }
}

/**
 * Popup shown every time the app is opened, unless the user taps "don't show
 * again" (persisted by the caller in app_prefs.dont_show_donate_dialog).
 */
@Composable
fun DonatePopupDialog(onDismiss: () -> Unit, onDontShowAgain: () -> Unit, strings: AppStrings) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DonateContent(strings)
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(strings.donateCloseBtn) }
                    Button(onClick = onDontShowAgain, modifier = Modifier.weight(1f)) { Text(strings.donateDontShowAgainBtn) }
                }
            }
        }
    }
}
@Composable
private fun SocialLinkRow(
    title: String,
    url: String
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        "Unable to open link",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Link,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
/**
 * "About" dialog reachable from the top bar menu; shows the app name,
 * version, and the same donate content as the popup.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit, versionName: String, strings: AppStrings) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.aboutDialogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(strings.appTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (versionName.isNotBlank()) {
                    Text(
                        strings.aboutVersionLabel(versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()

				Text(
				    strings.socialTitle,
				    style = MaterialTheme.typography.titleSmall,
				    fontWeight = FontWeight.Bold
				)
				
				Text(
				    strings.socialDescription,
				    style = MaterialTheme.typography.bodySmall,
				    color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				
				SocialLinkRow(
				    title = strings.telegramLabel,
				    url = "https://t.me/Idea_atmosphere"
				)
				
				SocialLinkRow(
				    title = strings.telegramTopicLabel,
				    url = "https://t.me/Idea_atmosphere_topic"
				)
				
				SocialLinkRow(
				    title = strings.githubLabel,
				    url = "https://github.com/Idea-atmosphere/Langosphere"
				)
                DonateContent(strings)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.donateCloseBtn) } }
    )
}
