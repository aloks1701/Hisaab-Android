package com.aloksharma.hisaab

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HisaabViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LedgerRepository(HisaabDatabase.get(app).transactions())
    private var demoIndex = 0

    val transactions = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ingest(text: String) {
        viewModelScope.launch { repo.ingest(text) }
    }

    /** Track 4 demo button: cycles through DEMO_NOTIFICATIONS on each tap. */
    fun ingestDemo() {
        val (pkg, text) = DEMO_NOTIFICATIONS[demoIndex++ % DEMO_NOTIFICATIONS.size]
        viewModelScope.launch { repo.ingest(text, sourcePackage = pkg) }
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

enum class Tab { LEDGER, STATS, SETTINGS }

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
    var masked by remember { mutableStateOf(prefs.getBoolean("masked", false)) }
    var demo by remember { mutableStateOf(prefs.getBoolean("demo", false)) }
    var tab by remember { mutableStateOf(Tab.LEDGER) }
    var period by remember { mutableStateOf(Period.current()) }

    val dark = darkOverride ?: isSystemInDarkTheme()
    val s = Strings.of(lang)
    val realTransactions by vm.transactions.collectAsState()
    // Demo mode swaps what is DISPLAYED. The database is never written to or read differently,
    // so turning it off gives the real ledger back untouched.
    val transactions = if (demo) remember { MockData.transactions() } else realTransactions
    val hasAccess = rememberNotificationAccess()

    val toggleMask = {
        masked = !masked
        prefs.edit().putBoolean("masked", masked).apply()
    }

    val toggleDemo = {
        demo = !demo
        prefs.edit().putBoolean("demo", demo).apply()
    }

    val openAccessSettings = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    // enableEdgeToEdge() draws under the status bar, so its icons have to be told which way to
    // go: dark icons on the cream paper, light icons on the dark theme. Without this the clock
    // and battery are invisible in light mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    HisaabTheme(dark = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // Material's default container is a lavender-tinted surfaceContainer, which
                // fights the paper palette. Pin it to the surface colour instead.
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            // Glyphs rather than an icon dependency: material-icons-extended is
                            // ~3MB of vectors for three symbols.
                            icon = { Text(glyphFor(entry), style = MaterialTheme.typography.titleMedium) },
                            label = { Text(labelFor(entry, s), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { insets ->
            when (tab) {
                Tab.LEDGER -> LedgerScreen(
                    lang = lang,
                    strings = s,
                    transactions = transactions,
                    dark = dark,
                    masked = masked,
                    period = period,
                    onPreviousPeriod = { period = period.previous() },
                    onNextPeriod = { period = period.next() },
                    hasAccess = hasAccess,
                    onToggleLang = {
                        lang = if (lang == Lang.HI) Lang.EN else Lang.HI
                        prefs.edit().putString("lang", lang.name).apply()
                    },
                    onToggleTheme = {
                        val next = !dark
                        darkOverride = next
                        prefs.edit().putBoolean("dark", next).apply()
                    },
                    onToggleMask = toggleMask,
                    onGrantAccess = openAccessSettings,
                    onSimulate = { vm.ingestDemo() },
                    showSimulate = !demo,
                    contentPadding = insets,
                )
                Tab.STATS -> StatsScreen(
                    strings = s,
                    transactions = transactions,
                    masked = masked,
                    period = period,
                    onPreviousPeriod = { period = period.previous() },
                    onNextPeriod = { period = period.next() },
                    contentPadding = insets,
                )
                Tab.SETTINGS -> SettingsScreen(
                    strings = s,
                    hasAccess = hasAccess,
                    masked = masked,
                    onToggleMask = toggleMask,
                    demo = demo,
                    onToggleDemo = toggleDemo,
                    onManageAccess = openAccessSettings,
                    contentPadding = insets,
                )
            }
        }
    }
}

private fun glyphFor(tab: Tab) = when (tab) {
    Tab.LEDGER -> "₹"
    Tab.STATS -> "▤"
    Tab.SETTINGS -> "⚙"
}

private fun labelFor(tab: Tab, s: Strings) = when (tab) {
    Tab.LEDGER -> s.tabLedger
    Tab.STATS -> s.tabStats
    Tab.SETTINGS -> s.tabSettings
}

/**
 * Notification access has no runtime-permission API - the grant lives in a system settings
 * list - so there is no result callback to listen to. Re-read it on ON_RESUME instead, which
 * is when the user comes back from that screen.
 */
@Composable
fun rememberNotificationAccess(): Boolean {
    val context = LocalContext.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return remember(resumeTick) {
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true
    }
}
