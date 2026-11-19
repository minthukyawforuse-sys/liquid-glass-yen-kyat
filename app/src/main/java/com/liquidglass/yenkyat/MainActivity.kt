package com.liquidglass.yenkyat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PriceBus.init(applicationContext)

        setContent {
            MainSetupScreen(
                hasOverlayPermission = { Settings.canDrawOverlays(this) },
                onRequestOverlayPermission = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                },
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onToggleOverlay = { start ->
                    val intent = Intent(this, LiquidGlassOverlayService::class.java)
                    if (start) {
                        if (Settings.canDrawOverlays(this)) {
                            startForegroundService(intent)
                        } else {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    } else {
                        stopService(intent)
                    }
                }
            )
        }
    }
}

@Composable
private fun MainSetupScreen(
    hasOverlayPermission: () -> Boolean,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit
) {
    val state by PriceBus.state.collectAsState()
    val context = LocalContext.current

    var jpyInput by remember(state.jpyRate) { mutableStateOf(state.jpyRate.toString()) }
    var cnyInput by remember(state.cnyRate) { mutableStateOf(state.cnyRate.toString()) }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF070B14), Color(0xFF131D36), Color(0xFF0B1120))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Liquid Glass ¥ → မြန်မာကျပ်",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "မည်သည့် Shopping App ပေါ်တွင်မဆို Floating Glass တင်ထားပြီး ဈေးနှုန်းအလိုအလျောက်ဖတ်ပြောင်းပေးမည်",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        GlassSectionCard(title = "၁။ လိုအပ်သော ခွင့်ပြုချက်များ ဖွင့်ရန်") {
            SetupActionRow(
                label = "Display Over Other Apps (Screen ပေါ်တင်ခွင့်)",
                buttonText = if (hasOverlayPermission()) "ခွင့်ပြုပြီး ✓" else "ဖွင့်မယ်",
                onClick = onRequestOverlayPermission
            )
            Spacer(Modifier.height(10.dp))
            SetupActionRow(
                label = "Accessibility Service (¥ ဈေးနှုန်း အလိုအလျောက်ဖတ်ရန်)",
                buttonText = "Settings ဖွင့်မယ်",
                onClick = onOpenAccessibilitySettings
            )
        }

        GlassSectionCard(title = "၂။ Exchange Rate ပြင်ဆင်ရန်") {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF7DD3FC),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color(0xFFBAE6FD),
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
            )

            OutlinedTextField(
                value = jpyInput,
                onValueChange = {
                    jpyInput = it
                    it.toDoubleOrNull()?.let { r -> PriceBus.updateRate(context, YenCurrency.JPY, r) }
                },
                label = { Text("🇯🇵 1 ဂျပန်ယန်း (JPY) = ကျပ်") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = cnyInput,
                onValueChange = {
                    cnyInput = it
                    it.toDoubleOrNull()?.let { r -> PriceBus.updateRate(context, YenCurrency.CNY, r) }
                },
                label = { Text("🇨🇳 1 တရုတ်ယွမ် (CNY) = ကျပ်") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (state.isOverlayRunning) {
                        Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF6366F1)))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .clickable { onToggleOverlay(!state.isOverlayRunning) }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isOverlayRunning) "Floating Overlay ပိတ်မယ်" else "Floating Liquid Glass ဖွင့်မယ်",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GlassSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFFBAE6FD),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SetupActionRow(
    label: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(end = 10.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .border(0.8.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = buttonText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
