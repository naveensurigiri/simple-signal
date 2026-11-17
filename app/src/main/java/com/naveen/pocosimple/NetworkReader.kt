package com.naveen.pocosimple

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class SimReading(
    val slot: Int,
    val carrier: String,
    val isData: Boolean,
    val dbm: Int?
)

data class NetReading(
    val onWifi: Boolean,
    val onMobile: Boolean,
    val sims: List<SimReading>,
    val dataCarrier: String?,
    val dataTech: String?,
    val dataDbm: Int?,
    val wifiName: String?,
    val wifiRssi: Int?,
    val wifiMbps: Int?
)

object NetworkReader {

    fun hasPhonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun read(context: Context): NetReading {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val onMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val sims = mutableListOf<SimReading>()
        var dataCarrier: String? = null
        var dataTech: String? = null
        var dataDbm: Int? = null

        if (hasPhonePermission(context)) {
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val defaultDataSub = SubscriptionManager.getDefaultDataSubscriptionId()

            for (info in subManager.activeSubscriptionInfoList.orEmpty()) {
                val tm = baseTm.createForSubscriptionId(info.subscriptionId)
                val dbm = readDbm(tm)
                val isData = info.subscriptionId == defaultDataSub
                val name = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM ${info.simSlotIndex + 1}"
                sims += SimReading(info.simSlotIndex + 1, name, isData, dbm)
                if (isData) {
                    dataCarrier = name
                    dataDbm = dbm
                    dataTech = techName(tm.dataNetworkType)
                }
            }
        }

        var wifiName: String? = null
        var wifiRssi: Int? = null
        var wifiMbps: Int? = null
        if (onWifi) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            wifiName = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            wifiRssi = info?.rssi?.takeIf { it > -127 }
            wifiMbps = info?.linkSpeed?.takeIf { it > 0 }
        }

        return NetReading(
            onWifi = onWifi,
            onMobile = onMobile,
            sims = sims,
            dataCarrier = dataCarrier,
            dataTech = dataTech,
            dataDbm = dataDbm,
            wifiName = wifiName,
            wifiRssi = wifiRssi,
            wifiMbps = wifiMbps
        )
    }

    @SuppressLint("MissingPermission")
    private fun readDbm(tm: TelephonyManager): Int? = try {
        tm.signalStrength?.cellSignalStrengths?.maxByOrNull { it.dbm }?.dbm
    } catch (e: SecurityException) {
        null
    }

    private fun techName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        else -> "Mobile"
    }

    /** Downloads about 10 MB for up to 10 seconds and returns speed in Mbps, or null on failure. */
    suspend fun downloadMbps(): Double? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://speed.cloudflare.com/__down?bytes=10000000")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val buffer = ByteArray(64 * 1024)
            var total = 0L
            val start = System.nanoTime()
            conn.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (System.nanoTime() - start > 10_000_000_000L) break
                }
            }
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            if (total == 0L || seconds <= 0.0) null else (total * 8 / 1_000_000.0) / seconds
        } catch (e: Exception) {
            null
        }
    }
}
