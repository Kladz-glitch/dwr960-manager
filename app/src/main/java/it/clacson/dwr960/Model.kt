package it.clacson.dwr960

import org.json.JSONArray
import org.json.JSONObject

data class Device(val name: String, val ip: String, val mac: String, val band: String, val lease: String)

data class Status(
    val connected: Boolean = false,
    val operator: String = "-",
    val service: String = "-",
    val rsrp: Int = 0, val rsrq: Int = 0, val sinr: Int = 0, val rssi: Int = 0,
    val band: String = "-", val earfcn: Int = 0, val mimo: String = "-",
    val cellId: Long = 0, val tac: Int = 0, val plmn: String = "-",
    val publicIp: String = "-", val dns1: String = "-",
    val uptimeSec: Long = 0, val simOk: Boolean = true, val roaming: Boolean = false,
    val rxBytes: Long = 0, val txBytes: Long = 0,
    val periodStart: String = "", val periodEnd: String = "",
    val downMbps: Double = 0.0, val upMbps: Double = 0.0,
    val devices: List<Device> = emptyList(),
    val wifi24: String = "-", val wifi5: String = "-",
    val lanIp: String = "-", val dhcp: String = "-",
    val fw: String = "-", val model: String = "DWR-960", val hwVer: String = "-", val modemVer: String = "-",
    val smsNew: Int = 0,
    val pingMs: Int = -1,
    val downHist: List<Double> = emptyList(),
    val upHist: List<Double> = emptyList(),
    val pingHist: List<Double> = emptyList(),
) {
    val usedBytes get() = rxBytes + txBytes
    val signalLevel: String get() = when {
        rsrp == 0 -> "Nullo"
        rsrp >= -80 -> "Eccellente"
        rsrp >= -90 -> "Buono"
        rsrp >= -100 -> "Discreto"
        rsrp >= -110 -> "Scarso"
        else -> "Nullo"
    }
    /** 0..4 tacche a partire dall'RSRP */
    val bars: Int get() = when (signalLevel) { "Eccellente" -> 4; "Buono" -> 3; "Discreto" -> 2; "Scarso" -> 1; else -> 0 }
}

/** Numeri di banda LTE usati dal firmware (da internet.js del router). */
fun bandName(code: Int): String = when (code) {
    in 120..134 -> "B${code - 119}".let { if (code == 134) "B17" else it }
    135 -> "B33"; 136 -> "B34"; 137 -> "B35"; 138 -> "B36"; 139 -> "B37"
    140 -> "B38"; 141 -> "B39"; 142 -> "B40"; 143 -> "B18"; 144 -> "B19"
    145 -> "B20"; 146 -> "B21"; 147 -> "B24"; 148 -> "B25"; 149 -> "B41"
    150 -> "B42"; 151 -> "B43"; 152 -> "B23"; 153 -> "B26"; 154 -> "B32"
    158 -> "B28"; 159 -> "B29"; 160 -> "B30"
    else -> if (code == 0) "-" else "cod. $code"
}

fun JSONObject.devices(key: String, band: String): List<Device> {
    val arr: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull {
        val o = arr.getJSONObject(it)
        if (o.optString("mac").isBlank()) null
        else Device(
            o.optString("hname").ifBlank { "Sconosciuto" },
            o.optString("ip"), o.optString("mac"), band, o.optString("leasetm"),
        )
    }
}

fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

fun fmtUptime(s: Long): String {
    val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60
    return when { d > 0 -> "$d${tr("g")} ${h}h"; h > 0 -> "${h}h ${m}m"; else -> "${m}m" }
}

/** "B20" -> "B20 (800 MHz)" e FDD/TDD */
fun bandInfo(b: String): Pair<String, String> {
    val n = b.removePrefix("B").toIntOrNull() ?: return b to ""
    val mhz = mapOf(1 to 2100, 2 to 1900, 3 to 1800, 4 to 1700, 5 to 850, 7 to 2600, 8 to 900, 12 to 700,
        13 to 700, 17 to 700, 18 to 800, 19 to 800, 20 to 800, 25 to 1900, 26 to 850, 28 to 700, 32 to 1500,
        38 to 2600, 39 to 1900, 40 to 2300, 41 to 2500, 42 to 3500, 43 to 3700)
    val duplex = if (n in 33..43) "TDD" else "FDD"
    return (mhz[n]?.let { "$b ($it MHz)" } ?: b) to duplex
}

data class ApnProfile(val apn: String, val uname: String, val pwd: String, val auth: Int)

data class ApnCfg(
    val netPref: Int, val autoConx: Int, val mode: Int,
    val apn: String, val uname: String, val passwd: String, val auth: Int, val pdp: Int,
    val list: List<ApnProfile>,
)

fun JSONObject.toApnCfg(): ApnCfg {
    val a = optJSONObject("apn")
    val c = a?.optJSONObject("COMMON"); val p = a?.optJSONObject("APN_0")
    val arr = optJSONObject("apn_list")?.optJSONArray("multi_apn0")
    val list = (0 until (arr?.length() ?: 0)).mapNotNull {
        val o = arr!!.optJSONObject(it) ?: return@mapNotNull null
        if (o.optString("apn").isBlank()) null
        else ApnProfile(o.optString("apn"), o.optString("uname"), o.optString("pwd"), o.optString("auth").toIntOrNull() ?: 3)
    }
    return ApnCfg(
        netPref = c?.optString("netpref_value")?.toIntOrNull() ?: 0,
        autoConx = c?.optString("auto_conx")?.toIntOrNull() ?: 1,
        mode = p?.optString("type")?.toIntOrNull() ?: 0,
        apn = p?.optString("apn").orEmpty(), uname = p?.optString("uname").orEmpty(),
        passwd = p?.optString("passwd").orEmpty(), auth = p?.optString("auth")?.toIntOrNull() ?: 3,
        pdp = p?.optString("pdp_type")?.toIntOrNull() ?: 0, list = list,
    )
}

data class Sms(val tag: String, val phone: String, val date: String, val text: String, val read: Boolean) {
    /** "26/10/10 04:07:40" (aa/mm/gg) -> "10/10/26 04:07" (gg/mm/aa) */
    val shownDate: String get() = runCatching {
        val (d, t) = date.split(" "); val (y, m, g) = d.split("/"); "$g/$m/$y ${t.take(5)}"
    }.getOrDefault(date)
}
