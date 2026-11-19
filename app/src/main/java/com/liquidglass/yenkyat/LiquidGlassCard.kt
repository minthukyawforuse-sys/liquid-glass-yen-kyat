package com.liquidglass.yenkyat

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LiquidGlassFloatingCard(
    onDragEvent: (MotionEvent) -> Boolean,
    onFocusableChange: (Boolean) -> Unit,
    onCloseOverlay: () -> Unit
) {
    val state by PriceBus.state.collectAsState()
    val context = LocalContext.current

    var isCollapsed by remember { mutableStateOf(false) }
    var isEditingRate by remember { mutableStateOf(false) }
    var rateDraft by remember(state.currency, state.activeRate) {
        mutableStateOf(formatDecimal(state.activeRate))
    }

    LaunchedEffect(isEditingRate) { onFocusableChange(isEditingRate) }

    val animatedMmk by animateIntAsState(
        targetValue = state.convertedMmk.roundToInt(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "mmkTicker"
    )

    val sweepProgress = remember { Animatable(-0.5f) }
    val popScale = remember { Animatable(1f) }

    LaunchedEffect(state.detectedAmount, state.activeRate, state.currency) {
        popScale.snapTo(1.06f)
        popScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
    }

    LaunchedEffect(state.detectedAmount, state.activeRate, state.currency) {
        sweepProgress.snapTo(-0.4f)
        sweepProgress.animateTo(1.5f, tween(720, easing = FastOutSlowInEasing))
    }

    val glassShape = if (isCollapsed) CircleShape else RoundedCornerShape(28.dp)
    val glassBgBrush = Brush.linearGradient(listOf(Color(0x521E293B), Color(0x3D0F172A), Color(0x4A1E293B)))
    val glassRimBrush = Brush.linearGradient(
        listOf(Color(0xCCFFFFFF), Color(0x807DD3FC), Color(0x2EFFFFFF), Color(0x88D8B4FE), Color(0xB3FFFFFF))
    )

    Box(
        modifier = Modifier
            .widthIn(min = if (isCollapsed) 220.dp else 306.dp, max = 324.dp)
            .clip(glassShape)
            .background(glassBgBrush)
            .drawWithContent {
                drawContent()
                val p = sweepProgress.value
                if (p > -0.35f && p < 1.45f) {
                    val x = size.width * p
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                            Offset(x - 90f, 0f),
                            Offset(x + 90f, size.height)
                        )
                    )
                }
            }
            .border(1.25.dp, glassRimBrush, glassShape)
            .padding(horizontal = 14.dp, vertical = if (isCollapsed) 10.dp else 12.dp)
    ) {
        if (isCollapsed) {
            Row(
                modifier = Modifier.fillMaxWidth().pointerInteropFilter { onDragEvent(it) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.autoScanEnabled) Color(0xFF34D399) else Color(0xFFFBBF24)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "¥${formatInt(state.detectedAmount.roundToInt())} → ${formatInt(animatedMmk)} Ks",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                GlassPillButton("▼") { isCollapsed = false }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().pointerInteropFilter { onDragEvent(it) }) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(0.45f)))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.autoScanEnabled) Color(0xFF34D399) else Color(0xFFFBBF24)))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.autoScanEnabled) "Auto-Read ON" else "Paused", color = Color.White.copy(0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassPillButton(if (state.autoScanEnabled) "Auto" else "Off", state.autoScanEnabled) { PriceBus.toggleAutoScan() }
                            GlassPillButton("—") { isEditingRate = false; isCollapsed = true }
                            GlassPillButton("✕") { onCloseOverlay() }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x45090D16))
                        .border(0.8.dp, Color.White.copy(0.16f), RoundedCornerShape(14.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    YenCurrency.entries.forEach { curr ->
                        val selected = state.currency == curr
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                                .background(if (selected) Color.White.copy(0.24f) else Color.Transparent)
                                .clickable { PriceBus.setCurrency(context, curr) }.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(curr.label, color = if (selected) Color.White else Color.White.copy(0.65f), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(state.sourceLabel, color = Color(0xFFBAE6FD), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (state.detectedPrices.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.detectedPrices.forEach { price ->
                            val isSelected = price == state.detectedAmount
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x6638BDF8) else Color.White.copy(0.12f))
                                    .border(0.8.dp, if (isSelected) Color(0xFF7DD3FC) else Color.White.copy(0.2f), RoundedCornerShape(10.dp))
                                    .clickable { PriceBus.selectManualPrice(price) }.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("¥${formatDecimal(price)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x520B1120))
                        .border(0.8.dp, Color.White.copy(0.16f), RoundedCornerShape(18.dp)).padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("ဖတ်ရရှိ (${state.currency.code})", color = Color.White.copy(0.65f), fontSize = 11.sp)
                        Text("¥${formatDecimal(state.detectedAmount)}", color = Color(0xFF7DD3FC), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.12f)))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { scaleX = popScale.value; scaleY = popScale.value },
                        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom
                    ) {
                        Text(formatInt(animatedMmk), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(5.dp))
                        Text("ကျပ်", color = Color(0xFFBAE6FD), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Box(
                        Modifier.align(Alignment.End).padding(top = 4.dp).clip(CircleShape).background(Color(0x2E34D399))
                            .border(0.6.dp, Color(0x5534D399), CircleShape).padding(horizontal = 9.dp, vertical = 2.dp)
                    ) {
                        Text(formatMyanmarLakh(animatedMmk.toDouble()), color = Color(0xFFA7F3D0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.10f))
                        .border(0.8.dp, Color.White.copy(0.2f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Exchange Rate", color = Color.White.copy(0.6f), fontSize = 10.sp)
                            Text("1 ¥ = ${formatDecimal(state.activeRate)} ကျပ်", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        GlassPillButton(if (isEditingRate) "ပိတ်မယ်" else "ပြင်မယ်", isEditingRate) {
                            isEditingRate = !isEditingRate
                            rateDraft = formatDecimal(state.activeRate)
                        }
                    }

                    AnimatedVisibility(visible = isEditingRate, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicTextField(
                                value = rateDraft,
                                onValueChange = { rateDraft = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                cursorBrush = SolidColor(Color(0xFF7DD3FC)),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0x66090D16))
                                    .border(1.dp, Color(0x807DD3FC), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8))))
                                    .clickable {
                                        rateDraft.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                                            PriceBus.updateRate(context, state.currency, it)
                                        }
                                        isEditingRate = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("သိမ်းမယ်", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPillButton(text: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (active) Color(0x4D34D399) else Color.White.copy(0.16f))
            .border(0.8.dp, if (active) Color(0x996EE7B7) else Color.White.copy(0.28f), CircleShape)
            .clickable { onClick() }.padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatInt(value: Int): String = NumberFormat.getNumberInstance(Locale.US).format(value)

private fun formatDecimal(value: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = 2
    return nf.format(value)
}

private fun formatMyanmarLakh(mmk: Double): String = when {
    mmk <= 0 -> "0 ကျပ်"
    mmk >= 100_000 -> String.format(Locale.US, "≈ %.2f သိန်း", mmk / 100_000.0)
    mmk >= 10_000 -> String.format(Locale.US, "≈ %.2f သောင်း", mmk / 10_000.0)
    else -> "${formatInt(mmk.roundToInt())} ကျပ်"
}
