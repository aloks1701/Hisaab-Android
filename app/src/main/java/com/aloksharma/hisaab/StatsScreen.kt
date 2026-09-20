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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private enum class Scope { MONTH, YEAR }

@Composable
fun StatsScreen(
    strings: Strings,
    transactions: List<Transaction>,
    masked: Boolean = false,
    period: Period = Period.current(),
    onPreviousPeriod: () -> Unit = {},
    onNextPeriod: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var scope by remember { mutableStateOf(Scope.MONTH) }

    val rows = remember(transactions, period, scope) {
        if (scope == Scope.MONTH) transactions.inPeriod(period) else transactions.inYear(period.year)
    }
    val summary = remember(rows) { summarize(rows) }
    val merchants = remember(rows) { topMerchants(rows) }
    val byMonth = remember(transactions, period, scope) {
        if (scope == Scope.YEAR) transactions.spendByMonth(period.year) else emptyList()
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scope == Scope.MONTH,
                    onClick = { scope = Scope.MONTH },
                    label = { Text(strings.monthView) },
                )
                FilterChip(
                    selected = scope == Scope.YEAR,
                    onClick = { scope = Scope.YEAR },
                    label = { Text(strings.yearView) },
                )
            }
        }

        if (scope == Scope.MONTH) {
            item { PeriodBar(period, strings, onPreviousPeriod, onNextPeriod) }
        } else {
            item {
                Text(
                    "${strings.yearTotal} ${period.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item { YearStrip(byMonth, strings, masked) }
        }

        if (rows.none { it.type == TxnType.DEBIT }) {
            item {
                Text(
                    strings.noStats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryTile(strings.txnCount, rows.size.toString(), Modifier.weight(1f))
                    SummaryTile(
                        strings.dailyAvg,
                        money(summary.spentPaise / daysIn(scope, period), masked),
                        Modifier.weight(1f),
                    )
                    SummaryTile(strings.biggest, money(biggestExpense(rows), masked), Modifier.weight(1f))
                }
            }

            item { Text(strings.byCategory, fontWeight = FontWeight.SemiBold) }
            item {
                CategoryDonut(
                    slices = summary.byCategory.entries.sortedByDescending { it.value }
                        .map { it.key to it.value },
                    centerLabel = if (scope == Scope.MONTH) strings.months[period.month] else period.year.toString(),
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (masked) Privacy.maskName(merchant) else merchant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            money(paise, masked),
                            style = moneyStyle(MaterialTheme.typography.bodyMedium),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Twelve months as vertical bars. Empty months keep their slot rather than being dropped, so
 * the gaps are visible - a year with three months of data should look like a year with three
 * months of data, not like a tidy three-bar chart.
 */
@Composable
private fun YearStrip(byMonth: List<Pair<Period, Long>>, strings: Strings, masked: Boolean) {
    if (byMonth.isEmpty()) return
    val max = byMonth.maxOf { it.second }.coerceAtLeast(1L)
    val total = byMonth.sumOf { it.second }

    Column(Modifier.fillMaxWidth()) {
        Text(
            money(total, masked),
            style = moneyStyle(MaterialTheme.typography.headlineLarge),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            byMonth.forEach { (p, paise) ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // A zero month still gets a hairline, so its slot reads as empty
                            // rather than as missing.
                            .height((92.dp * (paise.toFloat() / max)).coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (paise == 0L) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        strings.monthsShort[p.month],
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Donut rather than a filled pie: the hole carries the total, so the chart answers "how much"
 * and "on what" in one glance instead of needing a caption underneath.
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

            // 1.5 degrees of padding between slices keeps adjacent colours legible.
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
            Text(
                centerValue,
                style = moneyStyle(MaterialTheme.typography.titleLarge),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = moneyStyle(MaterialTheme.typography.titleSmall),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

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
            Text(amount, style = moneyStyle(MaterialTheme.typography.bodyMedium))
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color)
            )
        }
    }
}

/** Days to divide by: elapsed days of the month, or days elapsed in the year. */
private fun daysIn(scope: Scope, period: Period): Long =
    if (scope == Scope.MONTH) period.elapsedDays().toLong()
    else Period.monthsOf(period.year)
        .filter { it.startMs() <= System.currentTimeMillis() }
        .sumOf { it.elapsedDays().toLong() }
        .coerceAtLeast(1L)

internal fun topMerchants(transactions: List<Transaction>, limit: Int = 5): List<Pair<String, Long>> =
    transactions.filter { it.type == TxnType.DEBIT }
        .groupBy { it.merchant }
        .map { (merchant, rows) -> merchant to rows.sumOf { it.amountPaise } }
        .sortedByDescending { it.second }
        .take(limit)

internal fun biggestExpense(transactions: List<Transaction>): Long =
    transactions.filter { it.type == TxnType.DEBIT }.maxOfOrNull { it.amountPaise } ?: 0L

@Preview(showBackground = true)
@Composable
private fun StatsPreview() {
    HisaabTheme(dark = false) {
        StatsScreen(strings = Strings.of(Lang.HI), transactions = MockData.transactions())
    }
}
