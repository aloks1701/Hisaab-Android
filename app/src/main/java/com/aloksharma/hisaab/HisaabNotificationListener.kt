package com.aloksharma.hisaab

import android.app.Notification
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads posted notifications and hands their text to the ledger.
 *
 * Deliberately not restricted to an allowlist of payment app packages: the product has to
 * work with whichever bank app the user happens to have. The gate is the parser instead —
 * text without an amount, a direction word and a counterparty is dropped.
 * ponytail: a promotional notification quoting "₹500 off ... paid plans" can still slip
 * through; such rows land with needsReview = true. Add a package allowlist only if that
 * turns out to be noisy in practice.
 */
class HisaabNotificationListener : NotificationListenerService() {

    private companion object {
        const val TAG = "HisaabCapture"
        val CURRENCY = Regex("""₹|\bRs\.?\b|\bINR\b""", RegexOption.IGNORE_CASE)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = "$title $body".trim()

        // Logged BEFORE the blank check, because a notification arriving empty is exactly the
        // Android 15+ redaction case we need to be able to see. Debug builds only, and only for
        // notifications that are plausibly financial -- a blanket log would put every personal
        // message in logcat. Release builds log nothing.
        if (BuildConfig.DEBUG && looksFinancial(text)) {
            Log.d(TAG, "pkg=${sbn.packageName} len=${text.length} text=$text")
        }

        if (text.isBlank()) return

        val repo = LedgerRepository(HisaabDatabase.get(this).transactions())
        scope.launch {
            val id = repo.ingest(text, sbn.postTime, sbn.packageName)
            if (BuildConfig.DEBUG && looksFinancial(text)) {
                Log.d(TAG, "  -> stored=${id != null}")
            }
        }
    }

    /**
     * Requires actual currency text, NOT merely a known package. An earlier version also
     * accepted any package in the source-app list, which includes WhatsApp (for WhatsApp Pay)
     * -- so every personal WhatsApp message ended up in the debug log. Notification access is
     * a broad permission; the logging built on top of it has to be narrow.
     */
    private fun looksFinancial(text: String): Boolean = CURRENCY.containsMatchIn(text)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
