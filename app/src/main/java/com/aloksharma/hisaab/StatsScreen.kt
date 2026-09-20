package com.aloksharma.hisaab

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun StatsScreen(
    strings: Strings,
    transactions: List<Transaction>,
    masked: Boolean = false,
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
                    money(summary.spentPaise, masked),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (monthly.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryTile(strings.txnCount, monthly.size.toString(), Modifier.weight(1f))
                    SummaryTile(strings.dailyAvg, money(dailyAverage(summary.spentPaise), masked), Modifier.weight(1f))
                    SummaryTile(strings.biggest, money(biggestExpense(monthly), masked), Modifier.weight(1f))
                }
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
            item {
                CategoryDonut(
                    slices = summary.byCategory.entries.sortedByDescending { it.value }
                        .map { it.key to it.value },
                    centerLabel = strings.thisMonth,
                    centerValue = money(summary.spentPaise, masked),
                )
            }
            val max = summary.byCategory.values.max()
            items(summary.byCategory.entries.sortedByDescending { it.value }.toList()) { (category, paise) ->
                CategoryBar(
                    label = strings.categories[category] ?: category.name,
                    amount = money(paise, masked),
                    fraction = paise.toFloat() / max,
                    color = categoryColor(category),
                )
            }

            if (merchants.isNotEmpty()) {
                item { Text(strings.topMerchants, fontWeight = FontWeight.SemiBold) }
                items(merchants) { (merchant, paise) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (masked) Privacy.maskName(merchant) else merchant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(money(paise, masked), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Donut rather than a filled pie: the hole carries the month total, so the chart answers
 * "how much" and "on what" in one glance instead of needing a caption underneath.
 *
 * Slices below a whole percent are still drawn - dropping them would make the ring not add up,
 * and a ledger that visibly does not add up is worse than a sliver too thin to tap.
 */
@Composable
private fun CategoryDonut(
    slices: List<Pair<Category, Long>>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1L)
    val colors = slices.map { categoryColor(it.first) }
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 34.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke),
            )

            // 1.5 degrees of padding between slices, so adjacent colours stay legible.
            var start = -90f
            slices.forEachIndexed { i, (_, paise) ->
                val sweep = 360f * (paise.toFloat() / total)
                drawArc(
                    color = colors[i],
                    startAngle = start + 0.75f,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(centerValue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Spend so far this month divided by days elapsed, so early in a month it is not flattered. */
internal fun dailyAverage(spentPaise: Long, now: Long = System.currentTimeMillis()): Long {
    val daysElapsed = Calendar.getInstance().apply { timeInMillis = now }
        .get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    return spentPaise / daysElapsed
}

internal fun biggestExpense(transactions: List<Transaction>): Long =
    transactions.filter { it.type == TxnType.DEBIT }.maxOfOrNull { it.amountPaise } ?: 0L

@Composable
fun CategoryBar(
    label: String,
    amount: String,
    fraction: Float,
    color: Color = MaterialTheme.colorScheme.primary,
) {
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
                    .background(color)
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
