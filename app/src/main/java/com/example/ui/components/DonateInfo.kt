package com.example.ui.components

/**
 * Placeholder donation info for the "donate popup" and the "درباره" (About)
 * section (version 1.0.5).
 *
 * ── WHERE THIS FILE IS ──
 *   app/src/main/java/com/example/ui/components/DonateInfo.kt
 *
 * ── THE TOP "SUPPORT LINK" ROW ──
 *   The very first row in the popup / "درباره" section is a link to a
 *   donation / crowdfunding page (similar in style to a Boosty page, just
 *   pointing at whatever other website you choose). Replace
 *   SUPPORT_LINK_TITLE with that site/page's name and SUPPORT_LINK_URL with
 *   its real address. Tapping that row opens SUPPORT_LINK_URL in the
 *   device's browser.
 *
 * ── THE CRYPTO ADDRESS ROWS ──
 *   1. Replace BTC_ADDRESS below with your real Bitcoin address.
 *   2. Replace USDT_ADDRESS with your real Tether address. USDT exists on
 *      several networks (TRC20, ERC20, BEP20, ...) — keep the network name
 *      in the string (e.g. "T...xyz (TRC20)") so users send on the right
 *      one.
 *   3. Replace TON_ADDRESS with your real TON wallet address.
 *   Tapping any of these rows copies that address to the clipboard.
 *
 *   Save the file and rebuild the app — both the popup (shown on every
 *   launch) and the "درباره" section in the top-bar menu read directly
 *   from these constants, so nothing else needs to change.
 *
 * ── WHERE TO PUT YOUR OWN PICTURES ──
 *   The small icons are separate files in app/src/main/res/drawable:
 *     ic_donate_link.xml  (support-link picture placeholder)
 *     ic_donate_btc.xml   (Bitcoin picture placeholder)
 *     ic_donate_usdt.xml  (Tether picture placeholder)
 *     ic_donate_ton.xml   (TON picture placeholder)
 *   Each of those files has step-by-step instructions at the top for
 *   swapping in your own real picture (drag your .png/.jpg into the
 *   drawable folder, then update the matching "drawableRes" line in
 *   DonateDialog.kt to point at your new file).
 *
 * These are just placeholder strings; they are not a real link or real
 * wallet addresses.
 */
object DonateInfo {
    // Opened in the browser when the user taps the top row.
    const val SUPPORT_LINK_TITLE: String = "دارامت"
    const val SUPPORT_LINK_URL: String = "https://daramet.com/Idea_atmosphere"

    const val BTC_ADDRESS: String = "Soon or Never"
    const val USDT_ADDRESS: String = "Soon or Never"
    const val TON_ADDRESS: String = "Soon or Never"
}
