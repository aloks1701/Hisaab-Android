package com.aloksharma.hisaab

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun StatsScreen(
    strings: Strings,
    transactions: List<Transaction>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val monthly = remember(transactions) { transactions.filter { inCurrentMonth(it.timestamp) } }
    val summary = remember(monthly) { summarize(monthly) }
    val merchants = remember(monthly) { topMerchants(monthly) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(strings.thisMonth, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatPaise(summary.spentPaise),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (summary.byCategory.isEmpty()) {
            item {
                Text(
                    strings.noStats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        } else {
            item { Text(strings.byCategory, fontWeight = FontWeight.SemiBold) }
            val max = summary.byCategory.values.max()
            items(summary.byCategory.entries.sortedByDescending { it.value }.toList()) { (category, paise) ->
                CategoryBar(
                    label = strings.categories[category] ?: category.name,
                    amount = formatPaise(paise),
                    fraction = paise.toFloat() / max,
                )
            }

            if (merchants.isNotEmpty()) {
                item { Text(strings.topMerchants, fontWeight = FontWeight.SemiBold) }
                items(merchants) { (merchant, paise) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(merchant, style = MaterialTheme.typography.bodyMedium)
                        Text(formatPaise(paise), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBar(label: String, amount: String, fraction: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(amount, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Debits only, biggest first, capped at five so the list stays glanceable. */
internal fun topMerchants(transactions: List<Transaction>, limit: Int = 5): List<Pair<String, Long>> =
    transactions.filter { it.type == TxnType.DEBIT }
        .groupBy { it.merchant }
        .map { (merchant, rows) -> merchant to rows.sumOf { it.amountPaise } }
        .sortedByDescending { it.second }
        .take(limit)

internal fun inCurrentMonth(timestampMs: Long, now: Long = System.currentTimeMillis()): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
}

@Preview(showBackground = true)
@Composable
private fun StatsPreview() {
    HisaabTheme(dark = false) {
        StatsScreen(
            strings = Strings.of(Lang.HI),
            transactions = listOf(
                Transaction(1, TxnType.DEBIT, 85000, "Zomato", Category.FOOD, "", System.currentTimeMillis()),
                Transaction(2, TxnType.DEBIT, 49900, "Amazon", Category.SHOPPING, "", System.currentTimeMillis()),
            ),
        )
    }
}
