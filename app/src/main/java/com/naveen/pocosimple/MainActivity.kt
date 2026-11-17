package com.naveen.pocosimple

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SimpleSignalApp() }
    }
}

// ---- Colours ----
private val Black = Color(0xFF000000)
private val CardBg = Color(0xFF15171E)
private val White = Color(0xFFFFFFFF)
private val Muted = Color(0xFF9AA0A6)
private val Teal = Color(0xFF2DD4BF)
private val GreenGood = Color(0xFF34D399)
private val AmberOkay = Color(0xFFFBBF24)
private val RedWeak = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    background = Black,
    surface = CardBg,
    primary = Teal,
    onPrimary = Black,
    onBackground = White,
    onSurface = White
)

@Composable
fun SimpleSignalApp() {
    MaterialTheme(colorScheme = DarkColors) {
        Box(modifier = Modifier.fillMaxSize().background(Black)) {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var permissionTick by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<NetReading?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick++ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    // Refresh the readings every 2 seconds, and again whenever permissions change.
    LaunchedEffect(permissionTick) {
        while (true) {
            reading = withContext(Dispatchers.IO) { NetworkReader.read(context) }
            delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (tab) {
                0 -> HomeTab(reading, context)
                1 -> SimsTab(reading, context)
                else -> WifiTab(reading, context)
            }
        }
        BottomBar(selected = tab, onSelect = { tab = it })
    }
}

// ---- Home ----
@Composable
private fun HomeTab(reading: NetReading?, context: Context) {
    val scope = rememberCoroutineScope()
    var speed by remember { mutableStateOf<Double?>(null) }
    var testing by remember { mutableStateOf(false) }
    var speedFailed by remember { mutableStateOf(false) }

    val onWifi = reading?.onWifi == true
    val bigValue = if (onWifi) reading?.wifiRssi else reading?.dataDbm
    val quality = if (onWifi) wifiQuality(bigValue) else signalQuality(bigValue)
    val sourceLine = when {
        onWifi -> "Wi-Fi" + (reading?.wifiName?.let { " • $it" } ?: "")
        reading?.onMobile == true ->
            listOfNotNull(reading?.dataCarrier, reading?.dataTech).joinToString(" • ").ifBlank { "Mobile data" }
        else -> "Not connected"
    }

    Text(
        text = "Signal",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = White
    )

    InfoCard {
        Text(
            text = when {
                onWifi -> "You're on Wi-Fi"
                reading?.onMobile == true -> "You're on mobile data"
                else -> "No connection"
            },
            fontSize = 15.sp,
            color = Muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = bigValue?.toString() ?: "--",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "dBm",
                fontSize = 18.sp,
                color = Muted,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
        Text(
            text = quality.word,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = quality.color
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = sourceLine, fontSize = 15.sp, color = Muted)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BigButton(
            text = "Switch SIM",
            modifier = Modifier.weight(1f),
            filled = true
        ) { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) }

        BigButton(
            text = "Wi-Fi",
            modifier = Modifier.weight(1f),
            filled = false
        ) { openSettings(context, Settings.Panel.ACTION_WIFI) }
    }

    InfoCard {
        Text(
            text = "Speed check",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = White
        )
        Spacer(modifier = Modifier.height(8.dp))
        val result = speed
        Text(
            text = when {
                testing -> "Testing... this takes about 10 seconds"
                speedFailed -> "Could not test. Check your connection and try again."
                result != null -> "%.1f Mbps  •  %s".format(result, speedWord(result))
                else -> "Tap the button to see how fast your internet is."
            },
            fontSize = 15.sp,
            color = Muted
        )
        Spacer(modifier = Modifier.height(12.dp))
        BigButton(
            text = if (testing) "Testing..." else "Test speed",
            modifier = Modifier.fillMaxWidth(),
            filled = true
        ) {
            if (!testing) {
                testing = true
                speedFailed = false
                scope.launch {
                    val measured = NetworkReader.downloadMbps()
                    testing = false
                    if (measured == null) speedFailed = true else speed = measured
                }
            }
        }
    }
}

// ---- SIMs ----
@Composable
private fun SimsTab(reading: NetReading?, context: Context) {
    Text(text = "Your SIMs", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
    Text(
        text = "See each SIM and which one is used for mobile data.",
        fontSize = 15.sp,
        color = Muted
    )

    val sims = reading?.sims.orEmpty()
    if (sims.isEmpty()) {
        InfoCard {
            Text(
                text = "No SIM found yet. Allow phone access when asked, and check that your SIMs are inserted.",
                fontSize = 15.sp,
                color = Muted
            )
        }
    }

    sims.forEach { sim ->
        val quality = signalQuality(sim.dbm)
        InfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "SIM ${sim.slot}", fontSize = 15.sp, color = Muted)
                if (sim.isData) {
                    Pill(text = "Used for data", color = Teal)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = sim.carrier, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (sim.dbm == null) "No signal reading" else "${sim.dbm} dBm  •  ${quality.word}",
                fontSize = 16.sp,
                color = quality.color
            )
        }
    }

    BigButton(
        text = "Switch data SIM",
        modifier = Modifier.fillMaxWidth(),
        filled = true
    ) { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) }

    Text(
        text = "Android opens Network & internet. Tap SIMs, then pick the SIM you want for mobile data.",
        fontSize = 14.sp,
        color = Muted
    )
}

// ---- Wi-Fi ----
@Composable
private fun WifiTab(reading: NetReading?, context: Context) {
    val onWifi = reading?.onWifi == true

    Text(text = "Wi-Fi", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
    Text(text = "Your Wi-Fi connection at a glance.", fontSize = 15.sp, color = Muted)

    InfoCard {
        if (onWifi) {
            Text(text = "Connected", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GreenGood)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = reading?.wifiName ?: "Wi-Fi network",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Spacer(modifier = Modifier.height(10.dp))
            val quality = wifiQuality(reading?.wifiRssi)
            Text(text = "Signal: ${quality.word}", fontSize = 18.sp, color = quality.color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Speed link: ${reading?.wifiMbps?.toString() ?: "--"} Mbps",
                fontSize = 15.sp,
                color = Muted
            )
        } else {
            Text(text = "Not connected", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Turn on Wi-Fi and join a network to see it here.",
                fontSize = 15.sp,
                color = Muted
            )
        }
    }

    BigButton(
        text = "Open Wi-Fi settings",
        modifier = Modifier.fillMaxWidth(),
        filled = true
    ) { openSettings(context, Settings.ACTION_WIFI_SETTINGS) }

    Text(
        text = "The network name can show as hidden until you allow location access.",
        fontSize = 14.sp,
        color = Muted
    )
}

// ---- Bottom bar ----
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Home", "SIMs", "Wi-Fi")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) Teal else Muted,
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

// ---- Shared pieces ----
@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean,
    onClick: () -> Unit
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Black)
        ) {
            Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Muted)
        ) {
            Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ---- Plain-word ratings ----
private data class Quality(val word: String, val color: Color)

private fun signalQuality(dbm: Int?): Quality = when {
    dbm == null -> Quality("No reading", Muted)
    dbm >= -90 -> Quality("Great", GreenGood)
    dbm >= -100 -> Quality("Good", GreenGood)
    dbm >= -110 -> Quality("Okay", AmberOkay)
    else -> Quality("Weak", RedWeak)
}

private fun wifiQuality(rssi: Int?): Quality = when {
    rssi == null -> Quality("No reading", Muted)
    rssi >= -60 -> Quality("Great", GreenGood)
    rssi >= -70 -> Quality("Good", GreenGood)
    rssi >= -80 -> Quality("Okay", AmberOkay)
    else -> Quality("Weak", RedWeak)
}

private fun speedWord(mbps: Double): String = when {
    mbps < 5 -> "Slow, pages may take a while"
    mbps < 25 -> "OK for videos"
    else -> "Fast"
}

// ---- Open phone settings, with a fallback if a page is missing ----
private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
