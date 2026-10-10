package it.clacson.dwr960

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Client per l'interfaccia web del D-Link DWR-960 (login.cgi / data.ria / webpost.cgi). */
class RouterApi(private val host: String = "192.168.0.1") {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var user = "admin"
    private var pass = ""
    @Volatile private var sessId: String? = null
    private val base get() = "http://$host"

    class LoginFailed(val detail: String = "") : Exception("Credenziali non valide")

    suspend fun login(u: String, p: String) = withContext(Dispatchers.IO) {
        user = u; pass = p
        doLogin()
    }

    private fun doLogin() {
        // Il router vuole l'MD5 (esadecimale minuscolo) della password, non la password in chiaro.
        val once = java.security.MessageDigest.getInstance("MD5")
            .digest(pass.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val form = "uname=${URLEncoder.encode(user, "UTF-8")}&passwd=$once"
        val req = Request.Builder().url("$base/login.cgi")
            .post(form.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaTypeOrNull()))
            .browserHeaders("index.html").build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            val j = runCatching { JSONObject(txt) }.getOrNull()
            if (j == null || j.has("login_fail") || !j.has("SessId")) throw LoginFailed("HTTP ${r.code}: ${txt.take(120)}")
            sessId = j.getString("SessId")
        }
    }

    /** Chiude davvero la sessione sul router, così non resta "in uso" e il PC può entrare subito. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val id = sessId ?: return@withContext
        val body = JSONObject().put("logout", id).toString()
        val req = Request.Builder().url("$base/login.cgi")
            .header("Cookie", cookie()).browserHeaders()
            .post(body.toRequestBody("application/json".toMediaTypeOrNull())).build()
        runCatching { http.newCall(req).execute().use { it.body?.string() } }
        sessId = null
    }

    private fun Request.Builder.browserHeaders(page: String = "controlPanel.html") = this
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Origin", base)
        .header("Referer", "$base/$page")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
        .header("X-Requested-With", "XMLHttpRequest")

    private fun cookie() = "DWRLOGGEDID=$sessId; DWRLOGGEDUSER=$user; qSessId=$sessId"

    private fun rawGet(path: String): String {
        val req = Request.Builder().url("$base/$path")
            .header("Cookie", cookie()).browserHeaders().build()
        return http.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    private fun looksLoggedOut(t: String) =
        t.contains("login_fail") || t.trimStart().startsWith("<") || t.isBlank()

    /** GET con rilogin automatico se la sessione (300 s) è scaduta. */
    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        var t = if (sessId == null) "" else rawGet(path)
        if (looksLoggedOut(t)) { doLogin(); t = rawGet(path) }
        JSONObject(t)
    }

    private fun token(): String {
        var t = rawGet("data.ria?token=1")
        if (looksLoggedOut(t)) { doLogin(); t = rawGet("data.ria?token=1") }
        return t.trim()
    }

    /** POST JSON su webpost.cgi con authID. */
    suspend fun post(fields: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        fields.put("authID", token())
        val req = Request.Builder().url("$base/webpost.cgi")
            .header("Cookie", cookie()).browserHeaders()
            .post(fields.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
        val t = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        runCatching { JSONObject(t) }.getOrDefault(JSONObject())
    }

    // ---- API di alto livello ----
    suspend fun upStatus() = get("data.ria?DynUpdate=up_5s")
    suspend fun serviceInfo() = get("data.ria?DynUpdate=get_networkserviceinfo")
    suspend fun usage() = get("data.ria?DynUpdate=nw_usage")
    suspend fun clients() = get("data.ria?CfgType=get_client_list")
    suspend fun wifi() = get("data.ria?CfgType=get_wifi_cfg")
    suspend fun lan() = get("data.ria?CfgType=get_cfg&filetype=lan")
    suspend fun about() = get("data.ria?DynUpdate=about_sys")
    suspend fun smsInbox() = get("data.ria?CfgType=sms_action&cont=inbox&index=0")

    suspend fun apnCfg() = get("data.ria?CfgType=get_apn_cfg")

    private fun enc(x: String) = URLEncoder.encode(x, "UTF-8").replace("+", "%20")

    /** Tipo di rete preferito: 0 auto, 6 = 4G, 7 = 3G, 8 = 2G */
    suspend fun setNetPref(v: Int) = post(
        JSONObject().put("CfgType", "apn").put("COMMON", JSONObject().put("netpref_value", v.toString()))
    )

    /** type: 0 = APN automatico (dalla lista del router), 1 = manuale */
    suspend fun setApn(type: Int, apn: String, user: String, pass: String, auth: Int, pdp: Int) = post(
        JSONObject().put("CfgType", "apn").put(
            "APN_0", JSONObject().put("type", type).put("apn", enc(apn)).put("uname", enc(user))
                .put("passwd", enc(pass)).put("re_passwd", pass).put("auth", auth).put("pdp_type", pdp)
        )
    )

    /** connect = 0 disconnette, 1 connette */
    suspend fun connect(connect: Int) = post(
        JSONObject().put("connect", connect).put("ifc", if (connect == 1) "lte" else "all").put("CfgType", "connect_btn")
    )

    /** Cambia solo il nome (SSID) di una rete, rimandando invariato il resto della configurazione. ap = "AP1" (2.4 GHz) o "AP2" (5 GHz) */
    suspend fun setWifiSsid(ap: String, ssid: String, cur: JSONObject) = post(
        JSONObject().put("CfgType", "wifi_ap").put(ap, JSONObject()
            .put("ssid", enc(ssid))
            .put("hidden", cur.optString("hidden").toIntOrNull() ?: 0)
            .put("security", cur.optString("security").toIntOrNull() ?: 3)
            .put("wpa_auth", cur.optString("wpa_auth").toIntOrNull() ?: 4)
            .put("wpa_passphrase", cur.optString("wpa_passphrase"))
            .put("wep_key", "").put("wep_key_index", ""))
    )

    suspend fun reboot() = post(JSONObject().put("CfgType", "reboot_reset").put("cmd", "reboot"))
    suspend fun sendSms(to: String, msg: String) = post(
        JSONObject().put("CfgType", "sms_action").put("type", "sms_send")
            .put("msg", msg).put("phone_list", to)
    )
    suspend fun smsSetRead(tag: String) = post(JSONObject().put("CfgType", "sms_action").put("type", "inbox").put("cmd", "set_read").put("tag", tag))
    suspend fun smsDelete(tag: String) = post(JSONObject().put("CfgType", "sms_action").put("type", "inbox").put("cmd", "del").put("tag", tag))
    suspend fun ussdSend(code: String) = post(
        JSONObject().put("CfgType", "do_ussd").put("cmd", "send").put("code", code)
    )
    suspend fun ussdCancel() = post(JSONObject().put("CfgType", "do_ussd").put("cmd", "cancel"))
    suspend fun ussdState() = get("data.ria?DynUpdate=get_ussd_state")
}
