package it.clacson.dwr960

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class Vm(private val app: Application) : AndroidViewModel(app) {
    private val api = RouterApi()
    private val prefs = EncryptedSharedPreferences.create(
        app, "creds",
        MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val status = MutableStateFlow(Status())
    val loggedIn = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val limitGb = MutableStateFlow(prefs.getInt("limitGb", 100))
    val smsRaw = MutableStateFlow("")
    val smsList = MutableStateFlow<List<Sms>>(emptyList())
    val smsTotal = MutableStateFlow<Int?>(null)
    val themeMode = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val langPref = MutableStateFlow(prefs.getString("lang", "system") ?: "system")
    val notifOn = MutableStateFlow(prefs.getBoolean("notif", true))
    val ussdText = MutableStateFlow("")
    val toast = MutableStateFlow<String?>(null)
    val apn = MutableStateFlow<ApnCfg?>(null)
    val savedUser get() = prefs.getString("user", "admin") ?: "admin"
    val savedPass get() = prefs.getString("pass", "") ?: ""

    private var poll: Job? = null
    private var fails = 0; private var unreachable = false
    private var prevConn: Boolean? = null; private var prevSim: Boolean? = null
    private var prevNoSvc: Boolean? = null; private var prevSms = -1

    private fun alert(id: Int, title: String, text: String) {
        if (notifOn.value && title.isNotBlank()) Notif.send(app, id, title, text)
    }

    private fun checkAlerts(s: Status) {
        val noSvc = s.simOk && s.service.lowercase().let { it.isBlank() || it == "-" || "no service" in it || "nessun" in it || "no_service" in it }
        prevSim?.let { if (it && !s.simOk) alert(11, tr("SIM rimossa"), tr("Il router non rileva più la SIM")) }
        prevNoSvc?.let { if (!it && noSvc) alert(12, tr("Nessun servizio"), tr("Il modem non trova la rete dell'operatore")) }
        prevConn?.let {
            if (it && !s.connected && s.simOk && !noSvc) alert(13, tr("Disconnesso"), tr("La connessione dati è caduta"))
            if (!it && s.connected) alert(13, tr("Connessione ripristinata"), tr("La connessione dati è di nuovo attiva"))
        }
        if (prevSms >= 0 && s.smsNew > prevSms) alert(14, tr("Nuovo SMS"), tr("Hai %d nuovi SMS nel router").format(s.smsNew))
        prevSim = s.simOk; prevNoSvc = noSvc; prevConn = s.connected; prevSms = s.smsNew
    }
    private var lastRx = -1L; private var lastTx = -1L; private var lastT = 0L

    private fun bindWifi(app: Application) {
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { cm.bindProcessToNetwork(network) }
                })
        } catch (_: Exception) {}
    }

    private suspend fun measurePing(): Int = withContext(Dispatchers.IO) {
        try {
            val t0 = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 53), 1500) }
            ((System.nanoTime() - t0) / 1_000_000).toInt()
        } catch (_: Exception) { -1 }
    }

    init { bindWifi(app); Lang.pref = langPref.value }
    init { if (prefs.getBoolean("auto", false)) login(savedUser, savedPass, remember = false) }

    fun setTheme(m: String) { themeMode.value = m; prefs.edit().putString("theme", m).apply() }
    fun setLang(c: String) { langPref.value = c; Lang.pref = c; prefs.edit().putString("lang", c).apply() }

    fun setNotif(on: Boolean) { notifOn.value = on; prefs.edit().putBoolean("notif", on).apply() }
    fun setLimit(gb: Int) { limitGb.value = gb; prefs.edit().putInt("limitGb", gb).apply() }

    fun login(u: String, p: String, remember: Boolean = true) = viewModelScope.launch {
        busy.value = true; error.value = null
        try {
            api.login(u, p)
            if (remember) prefs.edit().putString("user", u).putString("pass", p).putBoolean("auto", true).apply()
            loggedIn.value = true
            startPolling()
        } catch (e: RouterApi.LoginFailed) {
            error.value = if (e.detail.contains("configured by IP"))
                tr("Il router è in uso dall'indirizzo %s. Esci dalla sua pagina web e riprova tra poco.").format(Regex("""\d+\.\d+\.\d+\.\d+""").find(e.detail)?.value ?: "?")
            else tr("Utente o password errati — risposta del router: %s").format(e.detail)
        } catch (e: Exception) {
            error.value = tr("Router non raggiungibile: sei collegato al suo Wi-Fi?")
        }
        busy.value = false
    }

    fun logout() {
        poll?.cancel(); poll = null
        prefs.edit().remove("pass").putBoolean("auto", false).apply()
        loggedIn.value = false
        error.value = null; busy.value = false
        status.value = Status()
        lastRx = -1L; lastTx = -1L
        prevConn = null; prevSim = null; prevNoSvc = null; prevSms = -1
        fails = 0; unreachable = false
        // chiusura della sessione sul router (in background, con tempo massimo)
        viewModelScope.launch { runCatching { kotlinx.coroutines.withTimeout(4000) { api.logout() } } }
    }

    private fun startPolling() {
        poll?.cancel()
        poll = viewModelScope.launch {
            var n = 0
            while (true) {
                try { refresh(slow = n % 6 == 0); error.value = null; fails = 0; if (unreachable) { unreachable = false; alert(10, tr("Router di nuovo raggiungibile"), "") } }
                catch (e: Exception) {
                    error.value = tr("Connessione al router persa")
                    if (++fails == 3 && !unreachable) { unreachable = true; alert(10, tr("Router non raggiungibile"), tr("Connessione al router persa")) }
                }
                n++; delay(5000)
            }
        }
    }

    private suspend fun refresh(slow: Boolean) {
        val up = api.upStatus(); val sv = api.serviceInfo(); val us = api.usage()
        val cl = api.clients()
        val modem = up.optJSONObject("signal")?.optJSONObject("modem")
        val v4 = up.optJSONObject("ipv4")
        val rx = us.optLong("rx"); val tx = us.optLong("tx"); val now = System.currentTimeMillis()
        var down = status.value.downMbps; var upl = status.value.upMbps
        if (lastRx >= 0 && now > lastT && rx >= lastRx && tx >= lastTx) {
            val dt = (now - lastT) / 1000.0
            down = (rx - lastRx) * 8 / 1e6 / dt; upl = (tx - lastTx) * 8 / 1e6 / dt
        }
        lastRx = rx; lastTx = tx; lastT = now
        val ping = measurePing()
        var s = status.value.copy(
            connected = v4?.optString("status") == "Connected",
            operator = sv.optString("oper_name", "-"), service = sv.optString("service_type", "-"),
            rsrp = sv.optInt("rsrp"), rsrq = sv.optInt("rsrq"), sinr = sv.optInt("sinr"), rssi = sv.optInt("rssi"),
            band = bandName(sv.optInt("bm_currentBand")), earfcn = sv.optInt("earfcn"),
            mimo = sv.optString("mimo", "-"), cellId = sv.optLong("cell_id"), tac = sv.optInt("tac"),
            plmn = sv.optString("PLMN", "-"), roaming = sv.optInt("roaming") == 1,
            publicIp = v4?.optString("ip") ?: "-", dns1 = v4?.optString("dns1") ?: "-",
            uptimeSec = up.optLong("system_uptime"),
            simOk = up.optJSONObject("sim_status")?.optInt("status") == 1,
            smsNew = up.optInt("new_sms"),
            rxBytes = rx, txBytes = tx,
            periodStart = us.optString("start"), periodEnd = us.optString("end"),
            downMbps = down, upMbps = upl, pingMs = ping,
            downHist = (status.value.downHist + down).takeLast(30),
            upHist = (status.value.upHist + upl).takeLast(30),
            pingHist = (status.value.pingHist + (if (ping < 0) 0.0 else ping.toDouble())).takeLast(30),
            devices = cl.devices("WiFi_2.4G_clilist", tr("Wi-Fi 2.4 GHz")) +
                cl.devices("WiFi_5G_clilist", tr("Wi-Fi 5 GHz")) + cl.devices("lan_clilist", "Cavo"),
        )
        if (modem != null && s.rsrp == 0) s = s.copy(rsrp = modem.optInt("strength"))
        if (slow) {
            val w = api.wifi().optJSONObject("wifi"); val l = api.lan().optJSONObject("lan")
            val a = api.about()
            s = s.copy(
                wifi24 = w?.optJSONObject("AP1")?.optString("ssid") ?: "-",
                wifi5 = w?.optJSONObject("AP2")?.optString("ssid") ?: "-",
                lanIp = l?.optJSONObject("LAN")?.optString("ip4_addr") ?: "-",
                dhcp = l?.optJSONObject("DHCP4S")?.let { "${it.optString("start_ip")} (+${it.optString("num")})" } ?: "-",
                fw = a.optString("fw_ver", "-"), model = a.optString("model_name", "DWR-960"),
                hwVer = a.optString("hw_ver", "-"), modemVer = a.optString("modem_ver", "-"),
            )
        }
        status.value = s
        checkAlerts(s)
    }

    fun loadSms() = viewModelScope.launch {
        try {
            val j = api.smsInbox()
            smsTotal.value = j.optInt("total", 0)
            smsList.value = j.keys().asSequence().filter { it.startsWith("M") }.mapNotNull { k ->
                j.optJSONObject(k)?.let { o ->
                    Sms(o.optString("tag"), o.optString("phone"), o.optString("date"), o.optString("msg"), o.optInt("read") == 1)
                }
            }.sortedByDescending { it.date }.toList()
            smsRaw.value = ""
        } catch (e: Exception) { toast.value = "${tr("Errore")}: ${e.message}" }
    }

    fun smsRead(tag: String) = viewModelScope.launch { runCatching { api.smsSetRead(tag) }; loadSms() }
    fun smsDelete(tag: String) = viewModelScope.launch {
        toast.value = try { api.smsDelete(tag); tr("SMS eliminato") } catch (e: Exception) { "${tr("Errore")}: ${e.message}" }
        loadSms()
    }

    fun sendSms(to: String, msg: String) = viewModelScope.launch {
        toast.value = try { api.sendSms(to, msg); tr("SMS inviato (controlla la risposta del router)") } catch (e: Exception) { "${tr("Invio fallito")}: ${e.message}" }
    }

    fun ussd(code: String) = viewModelScope.launch {
        ussdText.value = "Invio…"
        try {
            api.ussdSend(code)
            repeat(10) {
                delay(3000)
                val r = api.ussdState()
                val d = r.optString("resp_data")
                if (d.isNotBlank()) { ussdText.value = d; return@launch }
            }
            ussdText.value = tr("Nessuna risposta")
        } catch (e: Exception) { ussdText.value = "${tr("Errore")}: ${e.message}" }
    }

    fun reboot() = viewModelScope.launch {
        toast.value = try { api.reboot(); tr("Riavvio in corso, ci vorrà circa un minuto") } catch (e: Exception) { "${tr("Errore")}: ${e.message}" }
    }

    fun loadApn() = viewModelScope.launch {
        try { apn.value = api.apnCfg().toApnCfg() } catch (e: Exception) { toast.value = tr("Impossibile leggere le impostazioni di rete") }
    }

    private suspend fun applyReconnect(c: ApnCfg?) {
        // Come fa la pagina web: dopo il cambio si riavvia la connessione dati
        val wasConnected = status.value.connected
        api.connect(0)
        if (c?.autoConx == 0 && wasConnected) { delay(6000); api.connect(1) }
    }

    fun saveNetPref(v: Int) = viewModelScope.launch {
        toast.value = try {
            val r = api.setNetPref(v)
            if (r.optString("cmd_status") == "Done") { applyReconnect(apn.value); loadApn(); tr("Tipo di rete salvato: la connessione si riavvia") }
            else tr("Il router non ha accettato la modifica")
        } catch (e: Exception) { "${tr("Errore")}: ${e.message}" }
    }

    fun saveApn(type: Int, name: String, user: String, pass: String, auth: Int) = viewModelScope.launch {
        toast.value = try {
            val pdp = apn.value?.pdp ?: 0
            val r = api.setApn(type, name, user, pass, auth, pdp)
            if (r.optString("cmd_status") == "Done") { applyReconnect(apn.value); loadApn(); tr("APN salvato: la connessione si riavvia") }
            else tr("Il router non ha accettato l'APN")
        } catch (e: Exception) { "${tr("Errore")}: ${e.message}" }
    }

    /** ap = 1 (2.4 GHz) o 2 (5 GHz) */
    fun renameWifi(ap: Int, name: String) = viewModelScope.launch {
        val key = "AP$ap"
        toast.value = try {
            val cur = api.wifi().getJSONObject("wifi").getJSONObject(key)
            if (cur.optString("security") != "3") tr("Questa rete non usa WPA: modificala dalla pagina web del router")
            else {
                api.setWifiSsid(key, name, cur)
                delay(2500)
                val now = runCatching { api.wifi().getJSONObject("wifi").getJSONObject(key).optString("ssid") }.getOrNull()
                when (now) {
                    null -> tr("Nome inviato. Se eri collegato a quella rete, riconnettiti con il nuovo nome")
                    name -> tr("Nome della rete cambiato. Riconnettiti con il nuovo nome")
                    else -> tr("Il router non ha accettato il nuovo nome")
                }
            }
        } catch (e: Exception) { tr("Nome inviato. Se eri collegato a quella rete, riconnettiti con il nuovo nome") }
    }

    fun clearToast() { toast.value = null }
}
