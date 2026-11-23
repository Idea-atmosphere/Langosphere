package com.example.ui.components

/**
 * Donation info for the "donate popup" and the "درباره" (About) section.
 *
 * ── WHERE THIS FILE IS ──
 *   app/src/main/java/com/example/ui/components/DonateInfo.kt
 *
 * ── THE TOP "SUPPORT LINK" ROW ──
 *   The very first row in the popup / "درباره" section is a link to a
 *   donation page. SUPPORT_LINK_TITLE is the site/page name shown on the
 *   row and SUPPORT_LINK_URL its address; tapping the row opens the URL in
 *   the device's browser.
 *
 * ── THE CRYPTO ADDRESS ROWS ──
 *   One row per wallet, in this order: TON, Tron (TRX), Ethereum (ETH).
 *   Tapping a row copies that address to the clipboard. To change an
 *   address, edit the constant below, save and rebuild — both the popup
 *   (shown on every launch until dismissed for good) and the "درباره"
 *   section read straight from these constants.
 *
 * ── THE PICTURES ──
 *   The small icons are vector drawables in app/src/main/res/drawable:
 *     ic_donate_link.xml  (support-link picture)
 *     ic_donate_ton.xml   (TON symbol — ton.org brand assets)
 *     ic_donate_trx.xml   (TRON mark — tron.network)
 *     ic_donate_eth.xml   (ETH diamond, purple — ethereum.org/assets)
 *   The row titles come from strings_ui.xml (ton_title / tron_title /
 *   ethereum_title) so they follow the app language; the row colours and
 *   the drawable of each row are set in DonateDialog.kt (donateEntries).
 */
object DonateInfo {
    // Opened in the browser when the user taps the top row.
    const val SUPPORT_LINK_TITLE: String = "دارمت"
    const val SUPPORT_LINK_URL: String = "https://daramet.com/Idea_atmosphere"

    /** TON wallet (The Open Network). */
    const val TON_ADDRESS: String = "UQDivGkSK4EpCBz3Vzs2fDqH8P4Kz2jr9XugYpeqzPrHaHUP"

    /** Tron wallet (TRX, TRC20 network). */
    const val TRX_ADDRESS: String = "TCT4KvunT4vuSZrk9cqQCASj1uff4zpiJV"

    /** Ethereum wallet (ETH, ERC20 network). */
    const val ETH_ADDRESS: String = "0x272c9eE3b77da2619cA374f771E867268021c756"
}
