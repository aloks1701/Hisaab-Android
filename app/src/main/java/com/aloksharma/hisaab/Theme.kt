package com.aloksharma.hisaab

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Paper-and-ink palette, carried over from the web app so the two feel like one product:
 * warm off-white stock, near-black ink, an indigo accent with a saffron counterpoint.
 * Material's default purple made this look like an unfinished template.
 */
private val Bg = Color(0xFFF7F3EA)
private val Paper = Color(0xFFFFFDF8)
private val Ink = Color(0xFF201E1B)
private val Muted = Color(0xFF5B5850)
private val Line = Color(0xFFD9D0BC)
private val Accent = Color(0xFF2E3A6B)
private val Saffron = Color(0xFFC46A1D)
private val Debit = Color(0xFFAE3B2C)
private val Credit = Color(0xFF1F6F6B)
private val Chip = Color(0xFFEFE8D6)

private val BgD = Color(0xFF17151A)
private val PaperD = Color(0xFF1E1B22)
private val InkD = Color(0xFFEDE7D8)
private val MutedD = Color(0xFFA39E92)
private val LineD = Color(0xFF35303A)
private val AccentD = Color(0xFF7F8FD9)
private val SaffronD = Color(0xFFD2B15E)
private val DebitD = Color(0xFFE07A63)
private val CreditD = Color(0xFF4FB3AC)
private val ChipD = Color(0xFF29242E)

/** Debit/credit are semantic and have no slot in Material's scheme, so they travel separately. */
data class LedgerColors(
    val debit: Color,
    val credit: Color,
    val saffron: Color,
    val line: Color,
    val isDark: Boolean,
)

private val LocalLedgerColors = staticCompositionLocalOf {
    LedgerColors(Debit, Credit, Saffron, Line, isDark = false)
}

object HisaabTheme {
    val ledger: LedgerColors
        @Composable @ReadOnlyComposable get() = LocalLedgerColors.current
}

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Paper,
    secondary = Saffron,
    onSecondary = Paper,
    background = Bg,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Chip,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Debit,
)

private val DarkScheme = darkColorScheme(
    primary = AccentD,
    onPrimary = BgD,
    secondary = SaffronD,
    onSecondary = BgD,
    background = BgD,
    onBackground = InkD,
    surface = PaperD,
    onSurface = InkD,
    surfaceVariant = ChipD,
    onSurfaceVariant = MutedD,
    outline = LineD,
    outlineVariant = LineD,
    error = DebitD,
)

@Composable
fun HisaabTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ledger = if (dark) {
        LedgerColors(DebitD, CreditD, SaffronD, LineD, isDark = true)
    } else {
        LedgerColors(Debit, Credit, Saffron, Line, isDark = false)
    }
    CompositionLocalProvider(LocalLedgerColors provides ledger) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

/**
 * One colour per category for the breakdown chart. Earthy and desaturated so eleven of them can
 * sit next to each other on paper stock without turning into a pie-chart rainbow. Dark-mode
 * variants are lifted, not just the same hues on black, which would read muddy.
 */
private val CategoryLight = mapOf(
    Category.FOOD to Color(0xFFC46A1D),
    Category.GROCERIES to Color(0xFF1F6F6B),
    Category.FUEL to Color(0xFF2E3A6B),
    Category.TRANSPORT to Color(0xFF7A5C9E),
    Category.BILLS to Color(0xFF8A6A3D),
    Category.SHOPPING to Color(0xFFA8442F),
    Category.HEALTH to Color(0xFF3D7A6B),
    Category.RENT to Color(0xFF4A5568),
    Category.TRANSFER to Color(0xFF6B7280),
    Category.INCOME to Color(0xFF2F7D4F),
    Category.OTHER to Color(0xFF9A9384),
)

private val CategoryDark = mapOf(
    Category.FOOD to Color(0xFFD2B15E),
    Category.GROCERIES to Color(0xFF4FB3AC),
    Category.FUEL to Color(0xFF7F8FD9),
    Category.TRANSPORT to Color(0xFFB39BD4),
    Category.BILLS to Color(0xFFC2A070),
    Category.SHOPPING to Color(0xFFE07A63),
    Category.HEALTH to Color(0xFF6FB9A6),
    Category.RENT to Color(0xFF93A1B5),
    Category.TRANSFER to Color(0xFFAAB2BF),
    Category.INCOME to Color(0xFF6FBF8C),
    Category.OTHER to Color(0xFFBDB5A4),
)

@Composable
fun categoryColor(category: Category): Color =
    (if (HisaabTheme.ledger.isDark) CategoryDark else CategoryLight)[category]
        ?: MaterialTheme.colorScheme.onSurfaceVariant


/**
 * Money always sets in tabular figures.
 *
 * Proportional digits are the default, so "1" is narrower than "8" and a column of amounts
 * comes out visually ragged however carefully it is right-aligned. "tnum" gives every digit
 * the same advance width, which is the whole reason a ledger's numbers line up. This is the
 * difference between a list of transactions and a ledger.
 */
@Composable
fun moneyStyle(base: TextStyle = MaterialTheme.typography.bodyLarge): TextStyle =
    base.copy(fontFeatureSettings = "tnum")
