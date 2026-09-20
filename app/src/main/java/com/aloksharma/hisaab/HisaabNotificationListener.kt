package com.aloksharma.hisaab

import android.app.Notification
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = "$title $body".trim()
        if (text.isBlank()) return

        val repo = LedgerRepository(HisaabDatabase.get(this).transactions())
        scope.launch { repo.ingest(text, sbn.postTime, sbn.packageName) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
