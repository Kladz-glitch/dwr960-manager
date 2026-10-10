package it.clacson.dwr960

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ---- Colori fissi (la tavolozza chiaro/scuro è in Theme.kt) ----
private val Accent = Color(0xFF0A84FF)
private val Green = Color(0xFF22C55E)
private val Purple = Color(0xFF8B5CF6)
private val Orange = Color(0xFFFBA93A)
private val Red = Color(0xFFEB5054)
private val Slate = Color(0xFF6E7E95)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun Typography.withFont(f: FontFamily): Typography {
    fun TextStyle.u() = copy(fontFamily = f, letterSpacing = 0.sp)
    return copy(
        displayLarge = displayLarge.u(), displayMedium = displayMedium.u(), displaySmall = displaySmall.u(),
        headlineLarge = headlineLarge.u(), headlineMedium = headlineMedium.u(), headlineSmall = headlineSmall.u(),
        titleLarge = titleLarge.u(), titleMedium = titleMedium.u(), titleSmall = titleSmall.u(),
        bodyLarge = bodyLarge.u(), bodyMedium = bodyMedium.u(), bodySmall = bodySmall.u(),
        labelLarge = labelLarge.u(), labelMedium = labelMedium.u(), labelSmall = labelSmall.u(),
    )
}

class MainActivity : ComponentActivity() {
    private val vm: Vm by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            val pal = if (dark) DarkPal else LightPal
            SideEffect {
                window.statusBarColor = pal.bg.toArgb(); window.navigationBarColor = pal.bg.toArgb()
                window.decorView.setBackgroundColor(pal.bg.toArgb())
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                }
            }
            // Dimensione del testo fissa: non dipende dall'impostazione "carattere" del telefono
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f),
                LocalPal provides pal,
            ) {
                val scheme = if (dark) darkColorScheme(background = pal.bg, surface = pal.card, primary = Accent,
                    onBackground = pal.onBg, onSurface = pal.onBg)
                else lightColorScheme(background = pal.bg, surface = pal.card, primary = Accent,
                    onBackground = pal.onBg, onSurface = pal.onBg)
                MaterialTheme(colorScheme = scheme, typography = Typography().withFont(Inter)) {
                    Surface(Modifier.fillMaxSize(), color = pal.bg) { Root(vm) }
                }
            }
        }
    }
}

@Composable
fun Root(vm: Vm) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snack.showSnackbar(it); vm.clearToast() } }
    Scaffold(containerColor = P.bg, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.padding(pad)) { if (loggedIn) Main(vm, snack) else Login(vm) }
    }
}

@Composable
fun Login(vm: Vm) {
    var u by remember { mutableStateOf(vm.savedUser) }
    var p by remember { mutableStateOf("") }
    val err by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        if (P.dark) Icon(Icons.Default.Router, null, tint = Color.White, modifier = Modifier.size(72.dp))
        else Box(Modifier.size(84.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Router, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(tr("DWR-960"), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(tr("Manager"), color = Accent, fontSize = 18.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(u, { u = it }, label = { Text(tr("Utente")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(p, { p = it }, label = { Text(tr("Password del router")) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button({ vm.login(u, p) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(tr(if (busy) "Connessione…" else "Accedi"))
        }
        Text(tr("Collegati al Wi-Fi del router (192.168.0.1)"), color = P.muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp))
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Home), Devices("Dispositivi", Icons.Default.Devices),
    Sms("SMS", Icons.Default.Email), Net("Rete", Icons.Default.SignalCellularAlt), More("Altro", Icons.Default.MoreHoriz)
}

@Composable
private fun Main(vm: Vm, snack: SnackbarHostState) {
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    var overlay by remember { mutableStateOf<String?>(null) }
    val s by vm.status.collectAsStateWithLifecycle()
    BackHandler(enabled = overlay != null) { overlay = null }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (overlay == "settings") { SettingsScreen(vm) { overlay = null }; return@Box }
            when (tab) {
                Tab.Dashboard -> Dashboard(vm, s, { overlay = "settings" }) { tab = it }
                Tab.Devices -> DevicesScreen(s)
                Tab.Sms -> SmsScreen(vm)
                Tab.Net -> NetScreen(vm, s)
                Tab.More -> MoreScreen(vm, s)
            }
        }
        BottomBar(if (overlay != null) null else tab, s.smsNew > 0) { overlay = null; tab = it }
    }
}

@Composable
private fun BottomBar(sel: Tab?, smsBadge: Boolean, onSel: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(P.navBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(BorderStroke(1.dp, P.border), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Tab.values().forEach { t ->
            val on = t == sel
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (on) P.navSel else Color.Transparent)
                    .clickable { onSel(t) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BadgedBox(badge = { if (t == Tab.Sms && smsBadge) Badge(containerColor = Red) }) {
                    Icon(t.icon, t.label, tint = if (on) Accent else P.muted, modifier = Modifier.size(24.dp))
                }
                Text(tr(t.label), fontSize = 11.sp, maxLines = 1, color = if (on) Accent else P.muted,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

// ---------- Componenti grafici ----------
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(P.card)
            .border(BorderStroke(1.dp, P.border), RoundedCornerShape(20.dp)).padding(14.dp),
        content = content,
    )
}

@Composable
fun Stat(label: String, value: String, sub: String = "", modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(label, color = P.muted, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (sub.isNotEmpty()) Text(sub, color = P.muted, fontSize = 12.sp)
    }
}

@Composable
private fun SignalBars(bars: Int, color: Color, off: Color, modifier: Modifier) {
    val lit = bars
    Canvas(modifier) {
        val n = 4; val gap = size.width * 0.10f; val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bh = size.height * (0.30f + 0.70f * (i + 1) / n)
            drawRoundRect(if (i < lit) color else off, Offset(i * (bw + gap), size.height - bh),
                Size(bw, bh), CornerRadius(bw / 3))
        }
    }
}

@Composable
private fun Spark(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(0.001)
        val w = size.width; val h = size.height; val step = w / (values.size - 1)
        fun y(v: Double) = (h - (v / max * h * 0.80) - h * 0.06).toFloat()
        val line = Path(); val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * step; val yy = y(v)
            if (i == 0) { line.moveTo(x, yy); fill.moveTo(x, h); fill.lineTo(x, yy) }
            else {
                val px = (i - 1) * step; val py = y(values[i - 1]); val cx = (px + x) / 2
                line.cubicTo(cx, py, cx, yy, x, yy); fill.cubicTo(cx, py, cx, yy, x, yy)
            }
        }
        fill.lineTo(w, h); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

@Composable
private fun OptionalImage(name: String, modifier: Modifier, fallback: ImageVector) {
    val ctx = LocalContext.current
    val id = remember(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
    if (id != 0) Image(painterResource(id), null, modifier, contentScale = ContentScale.Fit)
    else Icon(fallback, null, modifier, tint = P.phIcon)
}

@Composable
private fun StatCell(icon: @Composable () -> Unit, label: String, value: String, sub: String,
                     subColor: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(P.cell)
            .border(BorderStroke(1.dp, P.border), RoundedCornerShape(16.dp)).padding(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon(); Spacer(Modifier.width(6.dp))
                Text(tr(label), color = P.muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false)
            Text(tr(sub), color = subColor, fontSize = 10.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun SpeedCard(icon: ImageVector, color: Color, label: String, value: String, unit: String,
                      hist: List<Double>, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(P.card)
            .border(BorderStroke(1.dp, P.border), RoundedCornerShape(18.dp)),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 10.dp, end = 6.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(28.dp).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text(tr(label), color = P.muted, fontSize = 11.sp, maxLines = 1, softWrap = false)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(2.dp))
                    Text(unit, fontSize = 10.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        Spark(hist, color, Modifier.fillMaxWidth().height(40.dp))
    }
}

private fun deviceIcon(n: String): ImageVector {
    val l = n.lowercase()
    return when {
        "tv" in l -> Icons.Default.Tv
        "desktop" in l || "pc" in l || "laptop" in l || "notebook" in l -> Icons.Default.Computer
        else -> Icons.Default.Smartphone
    }
}

@Composable
fun DeviceRow(d: Device) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(deviceIcon(d.name), null, tint = P.devIcon, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
            Text(d.ip, color = P.muted, fontSize = 11.sp)
        }
        if (d.band != "Cavo") Icon(Icons.Default.Wifi, null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(d.band.replace("Wi-Fi ", ""), color = P.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Tile(icon: ImageVector, color: Color, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(P.card)
            .border(BorderStroke(1.dp, P.border), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text(sub, color = P.muted, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
        }
    }
}

// ---------- Dashboard ----------
@Composable
private fun Dashboard(vm: Vm, s: Status, openSettings: () -> Unit, go: (Tab) -> Unit) {
    val limit by vm.limitGb.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf(false) }
    var editLimit by remember { mutableStateOf(false) }
    val (bandTxt, duplex) = bandInfo(s.band)
    val sigColor = when (s.signalLevel) { "Eccellente", "Buono" -> Green; "Discreto" -> Orange; else -> Red }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // Intestazione
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                OptionalImage("logo", Modifier.height(28.dp), Icons.Default.Router)
                Text(tr("DWR-960"), color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            IconButton(openSettings) { Icon(Icons.Default.Settings, null, Modifier.size(28.dp)) }
        }
        err?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }

        // Hero
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(P.cardTop, P.cardBot)))
                .border(BorderStroke(1.dp, P.border), RoundedCornerShape(22.dp)).padding(14.dp),
        ) {
            Row {
                Column(horizontalAlignment = Alignment.Start) {
                    Box(
                        Modifier.size(70.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(
                                if (s.connected) listOf(Color(0xFF2BC956), Color(0xFF0E8F34)) else listOf(Slate, Color(0xFF3A4656)))),
                        contentAlignment = Alignment.Center,
                    ) { SignalBars(s.bars, Color.White, Color.White.copy(alpha = .3f), Modifier.size(34.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.service.replace("LTE", "4G").ifBlank { "-" },
                        color = Green, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(P.badgeBg)
                            .border(BorderStroke(1.dp, Green), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr(if (s.connected) "Connesso" else if (!s.simOk) "SIM assente" else "Non connesso"),
                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                    )
                    Text("${s.operator} ${s.service}".replace("LTE", "4G (LTE)"), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Online da %s").format(fmtUptime(s.uptimeSec)), color = P.muted, fontSize = 12.sp)
                }
                OptionalImage("router_photo", Modifier.width(100.dp).height(90.dp), Icons.Default.Router)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatCell({ SignalBars(s.bars, Green, P.barOff, Modifier.size(16.dp)) },
                    "Segnale", "${s.rsrp} dBm", s.signalLevel, sigColor, Modifier.weight(1f))
                StatCell({ Icon(Icons.Default.CellTower, null, tint = P.muted, modifier = Modifier.size(16.dp)) },
                    "Banda", bandTxt, duplex, P.muted, Modifier.weight(1.15f))
                StatCell({ Icon(Icons.Default.CellTower, null, tint = P.muted, modifier = Modifier.size(18.dp)) },
                    "Cell ID", "${s.cellId}", "TAC: ${s.tac}", P.muted, Modifier.weight(1.1f))
                StatCell({ Icon(Icons.Default.Language, null, tint = P.muted, modifier = Modifier.size(18.dp)) },
                    "IP pubblico", s.publicIp, "IPv4", P.muted, Modifier.weight(1.2f))
            }
        }

        // Velocità
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedCard(Icons.Default.ArrowDownward, Accent, "Download", "%.1f".format(s.downMbps), "Mbps", s.downHist, Modifier.weight(1f))
            SpeedCard(Icons.Default.ArrowUpward, Purple, "Upload", "%.1f".format(s.upMbps), "Mbps", s.upHist, Modifier.weight(1f))
            SpeedCard(Icons.Default.Bolt, Green, "Ping", if (s.pingMs < 0) "–" else "${s.pingMs}", "ms", s.pingHist, Modifier.weight(1f))
        }

        // Utilizzo dati
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, tint = Accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(tr("Utilizzo dati"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(Modifier.clickable { detail = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Dettagli"), color = Accent, fontSize = 13.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = Accent)
                }
            }
            val usedGb = s.usedBytes / 1073741824.0
            val frac = (usedGb / limit).toFloat().coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text(fmtBytes(s.usedBytes), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(" / $limit GB", color = P.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp).clickable { editLimit = true })
                Spacer(Modifier.weight(1f))
                Text("${(frac * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(P.track)) {
                Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).fillMaxHeight().clip(CircleShape).background(Accent))
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Periodo: %s – %s").format(s.periodStart, s.periodEnd), color = P.muted, fontSize = 11.sp)
        }

        // Dispositivi
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, tint = Accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(tr("Dispositivi connessi (%d)").format(s.devices.size), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(Modifier.clickable { go(Tab.Devices) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Vedi tutti"), color = Accent, fontSize = 13.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = Accent)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (s.devices.isEmpty()) Text(tr("Nessun dispositivo"), color = P.muted, fontSize = 13.sp)
            s.devices.take(3).forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = P.border)
                DeviceRow(d)
            }
        }

        // Segnale (grande, con legenda delle tacche)
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(s.bars, sigColor, P.track, Modifier.size(46.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(tr(s.signalLevel), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = sigColor)
                    Text("RSRP ${s.rsrp} dBm  ·  SINR ${s.sinr} dB", color = P.muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(tr("Cosa significano le tacche"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            listOf(
                Triple(4, "Eccellente", "Segnale fortissimo: velocità al massimo."),
                Triple(3, "Buono", "Tutto fila liscio, anche video e videochiamate."),
                Triple(2, "Discreto", "Si naviga bene, ma i video in HD possono rallentare."),
                Triple(1, "Scarso", "Lento e instabile: prova a spostare il router vicino a una finestra."),
                Triple(0, "Nullo", "Nessun segnale utile: niente connessione dati."),
            ).forEach { (b, name, desc) ->
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(b, if (s.signalLevel == name) sigColor else P.muted, P.track, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(tr(name), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (s.signalLevel == name) sigColor else P.onBg)
                        Text(tr(desc), color = P.muted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Scorciatoie
        val tiles = listOf(
            Triple(Icons.Default.Wifi, Accent, "Wi-Fi" to "Reti e nomi"),
            Triple(Icons.Default.Email, Purple, "SMS" to "Leggi e invia"),
            Triple(Icons.Default.Dialpad, Green, "USSD" to "Codici rapidi"),
            Triple(Icons.Default.SignalCellularAlt, Orange, "Rete" to "Dettagli LTE"),
            Triple(Icons.Default.Tune, Accent, "Impostazioni" to "Lingua e tema"),
            Triple(Icons.Default.Settings, Slate, "Sistema" to "Riavvia"),
        )
        val targets = listOf(Tab.More, Tab.Sms, Tab.More, Tab.Net, Tab.More, Tab.More)
        tiles.chunked(3).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEachIndexed { i, (ic, col, t) ->
                    Tile(ic, col, tr(t.first), tr(t.second), Modifier.weight(1f)) {
                        if (t.first == "Impostazioni") openSettings() else go(targets[row * 3 + i])
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    if (detail) AlertDialog(
        onDismissRequest = { detail = false }, title = { Text(tr("Utilizzo dati")) },
        text = { Text(tr("Scaricati: %s\nInviati: %s\nTotale: %s\n\nPeriodo: %s – %s\n\nI contatori sono quelli del router e possono azzerarsi al riavvio.").format(fmtBytes(s.rxBytes), fmtBytes(s.txBytes), fmtBytes(s.usedBytes), s.periodStart, s.periodEnd)) },
        confirmButton = { TextButton({ detail = false }) { Text(tr("OK")) } })
    if (editLimit) {
        var t by remember { mutableStateOf(limit.toString()) }
        AlertDialog(onDismissRequest = { editLimit = false }, title = { Text(tr("Limite dati (GB)")) },
            text = { OutlinedTextField(t, { t = it.filter(Char::isDigit) }, singleLine = true) },
            confirmButton = { TextButton({ t.toIntOrNull()?.takeIf { it > 0 }?.let(vm::setLimit); editLimit = false }) { Text(tr("OK")) } })
    }
}

// ---------- Altre schermate ----------
@Composable
private fun DevicesScreen(s: Status) {
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(tr("Dispositivi connessi (%d)").format(s.devices.size), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(s.devices) { d ->
            Panel(Modifier.fillMaxWidth()) {
                DeviceRow(d); Text("MAC ${d.mac}", color = P.muted, fontSize = 12.sp)
                if (d.lease.isNotBlank()) Text(tr("Lease: %s s").format(d.lease), color = P.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SmsScreen(vm: Vm) {
    val list by vm.smsList.collectAsStateWithLifecycle()
    var delTag by remember { mutableStateOf<String?>(null) }
    val total by vm.smsTotal.collectAsStateWithLifecycle()
    var to by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { vm.loadSms(); kotlinx.coroutines.delay(4000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("SMS"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Nuovo messaggio"), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(to, { to = it }, label = { Text(tr("Numero")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(msg, { msg = it }, label = { Text(tr("Testo")) }, modifier = Modifier.fillMaxWidth())
            Button({ vm.sendSms(to, msg) }, enabled = to.isNotBlank() && msg.isNotBlank()) { Text(tr("Invia")) }
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Posta in arrivo"), fontWeight = FontWeight.SemiBold)
            if ((total ?: 0) == 0) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MarkEmailRead, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Qui regna il silenzio: o nessuno ti scrive, o hai fatto pulizia!"),
                        color = P.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else list.forEach { m ->
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(P.cell)
                    .clickable { if (!m.read) vm.smsRead(m.tag) }.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!m.read) Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
                        if (!m.read) Spacer(Modifier.width(6.dp))
                        Text(m.phone, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(m.shownDate, color = P.muted, fontSize = 11.sp)
                        IconButton({ delTag = m.tag }, Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, null, tint = P.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(m.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            TextButton({ vm.loadSms() }) { Text(tr("Aggiorna")) }
        }
    }
    delTag?.let { t ->
        AlertDialog(onDismissRequest = { delTag = null }, title = { Text(tr("Eliminare questo SMS?")) },
            confirmButton = { TextButton({ vm.smsDelete(t); delTag = null }) { Text(tr("Elimina")) } },
            dismissButton = { TextButton({ delTag = null }) { Text(tr("Annulla")) } })
    }
}

@Composable
private fun Chips(options: List<Pair<String, Int>>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, v) ->
            FilterChip(selected = selected == v, onClick = { onPick(v) }, label = { Text(tr(label), fontSize = 13.sp) })
        }
    }
}

@Composable
private fun NetScreen(vm: Vm, s: Status) {
    val cfg by vm.apn.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadApn() }
    var pendingPref by remember { mutableStateOf<Int?>(null) }
    var mode by remember(cfg) { mutableStateOf(cfg?.mode ?: 0) }
    var name by remember(cfg) { mutableStateOf(cfg?.apn.orEmpty()) }
    var user by remember(cfg) { mutableStateOf(cfg?.uname.orEmpty()) }
    var pass by remember(cfg) { mutableStateOf(cfg?.passwd.orEmpty()) }
    var auth by remember(cfg) { mutableStateOf(cfg?.auth ?: 3) }
    var confirmApn by remember { mutableStateOf(false) }
    val prefs = listOf("Auto" to 0, "4G" to 6, "3G" to 7, "2G" to 8)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Rete"), fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Tipo di rete"), fontWeight = FontWeight.SemiBold)
            Text(tr("Attuale: %s").format(s.service), color = P.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (cfg == null) Text(tr("Caricamento…"), color = P.muted, fontSize = 12.sp)
            else Chips(prefs, cfg!!.netPref) { if (it != cfg!!.netPref) pendingPref = it }
            Text(tr("Cambiando il tipo di rete la connessione dati si riavvia per qualche secondo."), color = P.muted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp))
        }

        Panel(Modifier.fillMaxWidth()) {
            Text(tr("APN (punto di accesso)"), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (cfg == null) Text(tr("Caricamento…"), color = P.muted, fontSize = 12.sp) else {
                Chips(listOf("Automatico" to 0, "Manuale" to 1), mode) { mode = it }
                Spacer(Modifier.height(8.dp))
                if (mode == 0) {
                    Text(tr("APN disponibili sul router"), color = P.muted, fontSize = 12.sp)
                    if (cfg!!.list.isEmpty()) Text(tr("Nessun APN in elenco: usa la modalità Manuale."), color = P.muted, fontSize = 12.sp)
                    Chips(cfg!!.list.mapIndexed { i, p -> p.apn to i }, cfg!!.list.indexOfFirst { it.apn.equals(name, true) }) { i ->
                        val p = cfg!!.list[i]; name = p.apn; user = p.uname; pass = p.pwd; auth = p.auth
                    }
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text(tr("APN")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(user, { user = it }, label = { Text(tr("Utente (facoltativo)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pass, { pass = it }, label = { Text(tr("Password (facoltativa)")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Autenticazione"), color = P.muted, fontSize = 12.sp)
                    Chips(listOf("Auto" to 3, "PAP" to 1, "CHAP" to 2, "Nessuna" to 0), auth) { auth = it }
                }
                Spacer(Modifier.height(10.dp))
                Button({ confirmApn = true }, enabled = name.isNotBlank()) { Text(tr("Salva APN")) }
                Text(tr("Un APN sbagliato toglie la connessione dati. Se succede, rimetti la modalità Automatico."),
                    color = P.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Dettagli LTE"), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            listOf("Operatore" to s.operator, "PLMN" to s.plmn, "Tecnologia" to s.service, "Banda" to bandInfo(s.band).first,
                "EARFCN" to s.earfcn.toString(), "MIMO" to s.mimo, "RSRP" to "${s.rsrp} dBm", "RSRQ" to "${s.rsrq} dB",
                "SINR" to "${s.sinr} dB", "RSSI" to "${s.rssi} dBm", "Cell ID" to s.cellId.toString(),
                "TAC" to s.tac.toString(), "Roaming" to if (s.roaming) "sì" else "no").forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr(k), color = P.muted, fontSize = 13.sp); Text(tr(v), fontSize = 13.sp)
                }
            }
        }
    }

    pendingPref?.let { v ->
        val label = prefs.first { it.second == v }.first
        AlertDialog(onDismissRequest = { pendingPref = null }, title = { Text(tr("Passare a %s?").format(label)) },
            text = { Text(if (v == 6 || v == 0) "La connessione dati si riavvia per qualche secondo."
                else "Con $label la velocità sarà molto più bassa e la rete potrebbe non essere disponibile. La connessione dati si riavvia.") },
            confirmButton = { TextButton({ vm.saveNetPref(v); pendingPref = null }) { Text(tr("Conferma")) } },
            dismissButton = { TextButton({ pendingPref = null }) { Text(tr("Annulla")) } })
    }
    if (confirmApn) AlertDialog(onDismissRequest = { confirmApn = false }, title = { Text(tr("Salvare l'APN?")) },
        text = { Text(tr("APN: %s\nLa connessione dati si riavvia per qualche secondo.").format(name)) },
        confirmButton = { TextButton({ vm.saveApn(mode, name.trim(), user, pass, auth); confirmApn = false }) { Text(tr("Salva")) } },
        dismissButton = { TextButton({ confirmApn = false }) { Text(tr("Annulla")) } })
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, color = P.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun MoreScreen(vm: Vm, s: Status) {
    val ussd by vm.ussdText.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf(false) }
    var askExit by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Int?>(null) }
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Altro"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Wi-Fi"), fontWeight = FontWeight.SemiBold)
            listOf(1 to ("2.4 GHz" to s.wifi24), 2 to ("5 GHz" to s.wifi5)).forEach { (ap, p) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.first, color = P.muted, fontSize = 12.sp)
                        Text(p.second, fontWeight = FontWeight.Medium)
                    }
                    if (p.second != "-") TextButton({ newName = p.second; rename = ap }) { Text(tr("Rinomina")) }
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("LAN"), fontWeight = FontWeight.SemiBold)
            InfoRow("Router", s.lanIp); InfoRow("DHCP", s.dhcp)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("USSD"), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(code, { code = it }, label = { Text(tr("Codice, es. *#...#")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({ vm.ussd(code) }, enabled = code.isNotBlank()) { Text(tr("Invia")) }
            if (ussd.isNotBlank()) Text(ussd, color = P.muted)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Sistema"), fontWeight = FontWeight.SemiBold)
            InfoRow(tr("Modello"), s.model); InfoRow("Firmware", s.fw)
            InfoRow("Hardware", s.hwVer); InfoRow("Modem", s.modemVer)
            InfoRow(tr("Operativo da"), fmtUptime(s.uptimeSec))
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Riavvio"), fontWeight = FontWeight.SemiBold)
            Text(tr("Riavvia il router: la connessione cadrà per circa un minuto."), color = P.muted, fontSize = 12.sp)
            Button({ confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Riavvia router")) }
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Esci"), fontWeight = FontWeight.SemiBold)
            OutlinedButton({ askExit = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Esci")) }
        }
    }
    if (askExit) AlertDialog(onDismissRequest = { askExit = false },
        title = { Text(tr("Vuoi davvero uscire?")) },
        text = { Text(tr("Dovrai inserire di nuovo la password del router.")) },
        confirmButton = { TextButton({ askExit = false; vm.logout() }) { Text(tr("Esci")) } },
        dismissButton = { TextButton({ askExit = false }) { Text(tr("Annulla")) } })
    rename?.let { ap ->
        AlertDialog(onDismissRequest = { rename = null }, title = { Text(tr("Nome della rete Wi-Fi")) },
            text = {
                Column {
                    OutlinedTextField(newName, { newName = it.take(32) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(tr("I dispositivi collegati a questa rete verranno scollegati e dovranno riconnettersi con il nuovo nome."),
                        color = P.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton({ vm.renameWifi(ap, newName.trim()); rename = null }, enabled = newName.isNotBlank()) { Text(tr("Salva")) } },
            dismissButton = { TextButton({ rename = null }) { Text(tr("Annulla")) } })
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(tr("Riavviare il router?")) }, text = { Text(tr("La connessione cadrà per circa un minuto.")) },
        confirmButton = { TextButton({ vm.reboot(); confirm = false }) { Text(tr("Riavvia")) } },
        dismissButton = { TextButton({ confirm = false }) { Text(tr("Annulla")) } })
}

// ---------- Impostazioni dell'app ----------
const val DONATE_URL = "https://www.paypal.com/paypalme/claudiorubin"

@Composable
private fun SettingsScreen(vm: Vm, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val lang by vm.langPref.collectAsStateWithLifecycle()
    val limit by vm.limitGb.collectAsStateWithLifecycle()
    val notif by vm.notifOn.collectAsStateWithLifecycle()
    var langDlg by remember { mutableStateOf(false) }
    var limDlg by remember { mutableStateOf(false) }
    val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "-" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text(tr("Impostazioni"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Aspetto"), fontWeight = FontWeight.SemiBold)
            Chips(listOf("Sistema" to 0, "Chiaro" to 1, "Scuro" to 2),
                when (theme) { "light" -> 1; "dark" -> 2; else -> 0 }) {
                vm.setTheme(when (it) { 1 -> "light"; 2 -> "dark"; else -> "system" })
            }
        }
        Panel(Modifier.fillMaxWidth().clickable { langDlg = true }) {
            Text(tr("Lingua"), fontWeight = FontWeight.SemiBold)
            Text(if (lang == "system") tr("Sistema") else Lang.names[lang] ?: lang, color = P.muted)
        }
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Notifiche"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Avvisi su disconnessioni, SIM rimossa, assenza di servizio e nuovi SMS (quando l'app è attiva)."), color = P.muted, fontSize = 12.sp)
                }
                Switch(notif, { vm.setNotif(it) })
            }
        }
        Panel(Modifier.fillMaxWidth().clickable { limDlg = true }) {
            Text(tr("Limite dati"), fontWeight = FontWeight.SemiBold)
            Text("$limit GB", color = P.muted)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text(tr("Informazioni"), fontWeight = FontWeight.SemiBold)
            InfoRow(tr("Versione app"), ver)
            InfoRow(tr("Sviluppata da"), "Clacson")
        }
        Button({ runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(DONATE_URL))) } }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Default.Favorite, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(tr("Offrimi un caffè con PayPal"))
        }
    }
    if (langDlg) AlertDialog(onDismissRequest = { langDlg = false }, title = { Text(tr("Lingua")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf("system") + Lang.order).forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { vm.setLang(c); langDlg = false }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(lang == c, null); Spacer(Modifier.width(10.dp))
                        Text(if (c == "system") tr("Sistema") else Lang.names[c] ?: c)
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton({ langDlg = false }) { Text(tr("Annulla")) } })
    if (limDlg) {
        var t by remember { mutableStateOf(limit.toString()) }
        AlertDialog(onDismissRequest = { limDlg = false }, title = { Text(tr("Limite dati")) },
            text = { OutlinedTextField(t, { t = it.filter(Char::isDigit).take(5) }, label = { Text("GB") }, singleLine = true) },
            dismissButton = { TextButton({ limDlg = false }) { Text(tr("Annulla")) } },
            confirmButton = { TextButton({ t.toIntOrNull()?.takeIf { it > 0 }?.let(vm::setLimit); limDlg = false }) { Text(tr("OK")) } })
    }
}
