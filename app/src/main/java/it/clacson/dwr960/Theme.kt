package it.clacson.dwr960

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Tavolozza colori: scura (campionata dal mockup) e chiara. */
class Pal(
    val dark: Boolean,
    val bg: Color, val card: Color, val cardTop: Color, val cardBot: Color, val border: Color,
    val muted: Color, val cell: Color, val navBg: Color, val navSel: Color, val track: Color,
    val barOff: Color, val badgeBg: Color, val devIcon: Color, val onBg: Color, val phIcon: Color,
)

val DarkPal = Pal(
    true, bg = Color(0xFF080F16), card = Color(0xFF0E1A22), cardTop = Color(0xFF0F1D2B), cardBot = Color(0xFF0B141D),
    border = Color(0xFF1B2B38), muted = Color(0xFF8A99B0), cell = Color(0xFF0C161E), navBg = Color(0xFF0B151B),
    navSel = Color(0xFF102F51), track = Color(0xFF253546), barOff = Color(0xFF2A4A35), badgeBg = Color(0xFF123D2C),
    devIcon = Color(0xFFC5D0DE), onBg = Color.White, phIcon = Color(0xFF2A3D52),
)

val LightPal = Pal(
    false, bg = Color(0xFFF2F5F9), card = Color.White, cardTop = Color.White, cardBot = Color(0xFFF6F9FC),
    border = Color(0xFFDCE4EE), muted = Color(0xFF5F6F85), cell = Color(0xFFF3F7FB), navBg = Color.White,
    navSel = Color(0xFFDCEBFF), track = Color(0xFFDDE5EF), barOff = Color(0xFFC9E7D2), badgeBg = Color(0xFFDDF5E6),
    devIcon = Color(0xFF4A5A70), onBg = Color(0xFF0B1620), phIcon = Color(0xFFB5C3D4),
)

val LocalPal = staticCompositionLocalOf { DarkPal }

val P: Pal @Composable @ReadOnlyComposable get() = LocalPal.current

/** Lingue: l'italiano è la lingua dei testi nel codice, le altre sono in Strings.kt */
object Lang {
    val order = listOf("it", "en", "es", "fr", "de", "pt", "ro", "nl", "pl", "tr", "ru", "zh")
    val names = mapOf(
        "it" to "Italiano", "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
        "pt" to "Português", "ro" to "Română", "nl" to "Nederlands", "pl" to "Polski", "tr" to "Türkçe",
        "ru" to "Русский", "zh" to "中文",
    )

    /** "system" oppure uno dei codici in [order]. Essendo uno stato, cambiarlo ridisegna l'interfaccia. */
    var pref by mutableStateOf("system")

    private val table: Map<String, Array<String>> by lazy { ROWS.associateBy { it[0] } }

    fun effective(): String {
        if (pref != "system") return pref
        val l = Locale.getDefault().language
        return if (l in order) l else "en"
    }

    fun tr(s: String): String {
        val code = effective()
        if (code == "it") return s
        val row = table[s] ?: return s
        val i = order.indexOf(code)
        return row.getOrNull(i + 1)?.takeIf { it.isNotEmpty() } ?: s
    }
}

fun tr(s: String) = Lang.tr(s)
