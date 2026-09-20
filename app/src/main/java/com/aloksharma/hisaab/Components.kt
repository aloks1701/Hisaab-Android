package com.aloksharma.hisaab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * Month stepper, shared by the ledger and the stats screen so both always agree on which
 * month is being shown.
 *
 * Stepping forward past the current month is blocked rather than hidden: a disabled arrow
 * says "this is the edge of your data", a missing one just looks broken.
 */
@Composable
fun PeriodBar(
    period: Period,
    strings: Strings,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val atPresent = period >= Period.current()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious) { Text("◀") }
        Text(
            "${strings.months[period.month]} ${period.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onNext, enabled = !atPresent) { Text("▶") }
    }
}

private operator fun Period.compareTo(other: Period): Int =
    compareValuesBy(this, other, { it.year }, { it.month })
