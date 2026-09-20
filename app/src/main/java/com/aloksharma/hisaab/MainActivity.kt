package com.aloksharma.hisaab

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HisaabViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LedgerRepository(HisaabDatabase.get(app).transactions())

    val transactions = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ingest(text: String) {
        viewModelScope.launch { repo.ingest(text) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HisaabApp() }
    }
}

private const val PREFS = "hisaab_prefs"

@Composable
fun HisaabApp(vm: HisaabViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var lang by remember {
        mutableStateOf(Lang.valueOf(prefs.getString("lang", null) ?: Lang.HI.name))
    }
    // null = follow the system setting, which is the default until the user picks a side.
    var darkOverride by remember {
        mutableStateOf(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null)
    }
    val dark = darkOverride ?: androidx.compose.foundation.isSystemInDarkTheme()
    val s = Strings.of(lang)
    val transactions by vm.transactions.collectAsState()

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
            Dashboard(
                strings = s,
                transactions = transactions,
                dark = dark,
                onToggleLang = {
                    lang = if (lang == Lang.HI) Lang.EN else Lang.HI
                    prefs.edit().putString("lang", lang.name).apply()
                },
                onToggleTheme = {
                    val next = !dark
                    darkOverride = next
                    prefs.edit().putBoolean("dark", next).apply()
                },
                onGrantAccess = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onSimulate = { vm.ingest(nextDemoNotification()) },
                contentPadding = insets,
            )
        }
    }
}

private var demoIndex = 0
private fun nextDemoNotification(): String =
    DEMO_NOTIFICATIONS[demoIndex++ % DEMO_NOTIFICATIONS.size]

@Composable
fun Dashboard(
    strings: Strings,
    transactions: List<Transaction>,
    dark: Boolean,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    onGrantAccess: () -> Unit,
    onSimulate: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val summary = summarize(transactions)
    val context = LocalContext.current
    val hasAccess = remember(transactions.size) { hasNotificationAccess(context) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("हिसाब", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(strings.tagline, style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    TextButton(onClick = onToggleLang) {
                        Text(if (strings == Strings.of(Lang.HI)) "EN" else "हिं")
                    }
                    TextButton(onClick = onToggleTheme) { Text(if (dark) "☀" else "☾") }
                }
            }
        }

        if (!hasAccess) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
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
                TotalTile(strings.spent, formatPaise(summary.spentPaise), Modifier.weight(1f))
                TotalTile(strings.received, formatPaise(summary.receivedPaise), Modifier.weight(1f))
                TotalTile(strings.net, formatPaise(summary.netPaise), Modifier.weight(1f))
            }
        }

        if (summary.byCategory.isNotEmpty()) {
            item { Text(strings.byCategory, fontWeight = FontWeight.SemiBold) }
            val max = summary.byCategory.values.max()
            items(summary.byCategory.entries.sortedByDescending { it.value }.toList()) { (category, paise) ->
                CategoryBar(
                    label = strings.categories[category] ?: category.name,
                    amount = formatPaise(paise),
                    fraction = paise.toFloat() / max,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.recent, fontWeight = FontWeight.SemiBold)
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = onSimulate) { Text(strings.simulate) }
                }
            }
        }

        if (transactions.isEmpty()) {
            item { Text(strings.empty, style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(transactions, key = { it.id }) { txn ->
                TransactionRow(txn, strings)
            }
        }
    }
}

@Composable
private fun TotalTile(label: String, amount: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryBar(label: String, amount: String, fraction: Float) {
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

@Composable
private fun TransactionRow(txn: Transaction, strings: Strings) {
    val incoming = txn.type == TxnType.CREDIT || txn.type == TxnType.REFUND
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(txn.merchant, fontWeight = FontWeight.Medium)
            Row {
                Text(
                    strings.categories[txn.category] ?: txn.category.name,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (txn.needsReview) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strings.needsReview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        Text(
            (if (incoming) "+" else "−") + formatPaise(txn.amountPaise),
            fontWeight = FontWeight.SemiBold,
            color = if (incoming) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** No runtime-permission API for notification access — the grant lives in a system settings list. */
private fun hasNotificationAccess(context: Context): Boolean =
    Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.contains(context.packageName) == true

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    MaterialTheme {
        Dashboard(
            strings = Strings.of(Lang.HI),
            transactions = listOf(
                Transaction(1, TxnType.DEBIT, 85000, "Zomato", Category.FOOD, "", 0),
                Transaction(2, TxnType.CREDIT, 120000, "rahul.s", Category.INCOME, "", 0),
            ),
            dark = false,
            onToggleLang = {}, onToggleTheme = {}, onGrantAccess = {}, onSimulate = {},
        )
    }
}
