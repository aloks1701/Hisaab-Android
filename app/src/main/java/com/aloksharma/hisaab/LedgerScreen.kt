package com.aloksharma.hisaab

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun LedgerScreen(
    lang: Lang,
    strings: Strings,
    transactions: List<Transaction>,
    dark: Boolean,
    masked: Boolean,
    hasAccess: Boolean,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleMask: () -> Unit,
    onGrantAccess: () -> Unit,
    onSimulate: () -> Unit,
    showSimulate: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val summary = summarize(transactions)
    val context = LocalContext.current
    val grouped = remember(transactions) { transactions.groupBy { dayKeyOf(it.timestamp) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Masthead(lang, strings, dark, masked, onToggleLang, onToggleTheme, onToggleMask) }

        if (!hasAccess) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, HisaabTheme.ledger.line),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.grantTitle, fontWeight = FontWeight.SemiBold)
                        Text(strings.grantBody, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onGrantAccess) { Text(strings.grantButton) }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TotalTile(strings.spent, money(summary.spentPaise, masked), Modifier.weight(1f))
                TotalTile(strings.received, money(summary.receivedPaise, masked), Modifier.weight(1f))
                TotalTile(strings.net, money(summary.netPaise, masked), Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.recent, fontWeight = FontWeight.SemiBold)
                if (BuildConfig.DEBUG && showSimulate) {
                    TextButton(onClick = onSimulate) { Text(strings.simulate) }
                }
            }
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    strings.empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        } else {
            grouped.forEach { (dayStart, rows) ->
                item(key = "day-$dayStart") {
                    Text(
                        dayHeaderLabel(context, strings, dayStart),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(rows, key = { it.id }) { txn -> TransactionRow(txn, strings, masked) }
            }
        }
    }
}

@Composable
private fun Masthead(
    lang: Lang,
    strings: Strings,
    dark: Boolean,
    masked: Boolean,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleMask: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // The indigo-to-saffron rule from the web masthead, the one piece of ornament here.
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, HisaabTheme.ledger.saffron)
                    )
                )
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(fill = false) lets the tagline wrap in the space left by the toggles
            // rather than forcing the row wider than the screen.
            Column(Modifier.weight(1f, fill = false)) {
                Text(strings.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    strings.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row {
                TextButton(onClick = onToggleMask) { Text(if (masked) "🙈" else "👁") }
                TextButton(onClick = onToggleLang) { Text(if (lang == Lang.HI) "EN" else "हिं") }
                TextButton(onClick = onToggleTheme) { Text(if (dark) "☀" else "☾") }
            }
        }
    }
}

@Composable
private fun TotalTile(label: String, amount: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, HisaabTheme.ledger.line),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** A small outlined chip naming where the row came from: GPay, PhonePe, HDFC. */
@Composable
private fun SourcePill(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(1.dp, HisaabTheme.ledger.line, RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun TransactionRow(txn: Transaction, strings: Strings, masked: Boolean) {
    val incoming = txn.type == TxnType.CREDIT || txn.type == TxnType.REFUND
    val context = LocalContext.current
    val timeLabel = remember(txn.timestamp) {
        DateUtils.formatDateTime(context, txn.timestamp, DateUtils.FORMAT_SHOW_TIME)
    }
    val source = remember(txn.id) { SourceApp.label(txn.sourcePackage, txn.sourceText) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (masked) Privacy.maskName(txn.merchant) else txn.merchant,
                    fontWeight = FontWeight.Medium,
                )
                if (source != null) {
                    Spacer(Modifier.width(8.dp))
                    SourcePill(source)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.categories[txn.category] ?: txn.category.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " · $timeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (txn.needsReview) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strings.needsReview,
                        style = MaterialTheme.typography.bodySmall,
                        color = HisaabTheme.ledger.saffron,
                    )
                }
            }
        }
        Text(
            (if (incoming) "+" else "−") + money(txn.amountPaise, masked),
            fontWeight = FontWeight.SemiBold,
            color = if (incoming) HisaabTheme.ledger.credit else HisaabTheme.ledger.debit,
        )
    }
}

/** Formats money, masking the digits when private mode is on. */
internal fun money(paise: Long, masked: Boolean): String =
    formatPaise(paise).let { if (masked) Privacy.maskMoney(it) else it }

/** Local-midnight timestamp for a transaction's calendar day, used as its day-header group key. */
private fun dayKeyOf(timestampMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestampMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayHeaderLabel(context: Context, strings: Strings, dayStartMs: Long): String = when {
    DateUtils.isToday(dayStartMs) -> strings.today
    DateUtils.isToday(dayStartMs + DateUtils.DAY_IN_MILLIS) -> strings.yesterday
    else -> DateUtils.formatDateTime(
        context, dayStartMs,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
    )
}

@Preview(showBackground = true)
@Composable
private fun LedgerPreview() {
    HisaabTheme(dark = false) {
        LedgerScreen(
            lang = Lang.HI,
            strings = Strings.of(Lang.HI),
            transactions = listOf(
                Transaction(1, TxnType.DEBIT, 85000, "Zomato", Category.FOOD, "", 0,
                    sourcePackage = "com.google.android.apps.nbu.paisa.user"),
                Transaction(2, TxnType.CREDIT, 120000, "rahul.s", Category.INCOME, "", 0,
                    sourcePackage = "com.snapwork.hdfc"),
            ),
            dark = false,
            masked = false,
            hasAccess = true,
            onToggleLang = {}, onToggleTheme = {}, onToggleMask = {}, onGrantAccess = {}, onSimulate = {},
        )
    }
}
