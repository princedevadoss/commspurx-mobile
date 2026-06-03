package com.commspurx.mobile.data.local

/** Badge and summary notification caps (WhatsApp-style "50+" when there are more). */
object BadgeCounts {
    const val DISPLAY_CAP = 50
    const val MOBILE_PAGE_SIZE = 30
    const val BACKGROUND_SYNC_CAP = 50

    fun unseen(currentCount: Int, seenCount: Int): Int =
        (currentCount - seenCount).coerceAtLeast(0)

    /** Value for numeric badge UI (capped at [DISPLAY_CAP]). */
    fun badgeValue(unseen: Int): Int = unseen.coerceAtMost(DISPLAY_CAP)

    /** True when the real unseen count exceeds the display cap. */
    fun hasOverflow(unseen: Int): Boolean = unseen > DISPLAY_CAP

    /** e.g. "50+" or "12" */
    fun badgeLabel(unseen: Int): String {
        val capped = badgeValue(unseen)
        return if (hasOverflow(unseen)) "$DISPLAY_CAP+" else capped.toString()
    }

    /** Summary line for OS / in-app banners, e.g. "54 pending purchase contracts". */
    fun summaryLabel(count: Int, singular: String, plural: String): String {
        val noun = if (count == 1) singular else plural
        return "$count $noun"
    }
}
