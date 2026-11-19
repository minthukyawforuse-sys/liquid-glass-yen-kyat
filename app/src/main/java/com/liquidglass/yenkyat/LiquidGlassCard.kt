package com.liquidglass.yenkyat

import android.content.Intent
import android.provider.Settings
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
internal fun LiquidGlassQuickBallRoot(
    onBallTouch: (MotionEvent, () -> Unit) -> Boolean,
    onPillDragTouch: (MotionEvent) -> Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onFocusableChange: (Boolean) -> Unit,
    onCloseOverlay: () -> Unit
) {
    val state by PriceBus.state.collectAsState()
    val context = LocalContext.current

    var isExpanded by remember { mutableStateOf(false) }
    var isEditingRate by remember { mutableStateOf(false) }
    var rateDraft by remember(state.currency, state.activeRate) { mutableStateOf(formatDec(state.activeRate)) }

    LaunchedEffect(isEditingRate) { onFocusableChange(isEditingRate) }

    val animatedMmk by animateIntAsState(
        targetValue = state.convertedMmk.roundToInt(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "mmkTicker"
    )

    val sweepProgress = remember { Animatable(-0.5f) }
    val popScale = remember { Animatable(1f) }

    LaunchedEffect(state.detectedAmount, state.activeRate, state.currency, isExpanded) {
        if (isExpanded) {
            popScale.snapTo(1.07f)
            popScale.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 420f))
        }
    }

    LaunchedEffect(state.detectedAmount, state.activeRate, state.currency, isExpanded) {
        if (isExpanded) {
            sweepProgress.snapTo(-0.4f)
            sweepProgress.animateTo(1.5f, tween(680, easing = FastOutSlowInEasing))
        }
    }

    val glassRim = Brush.linearGradient(
        listOf(Color(0xF0FFFFFF), Color(0xAA7DD3FC), Color(0x45FFFFFF), Color(0xAAD8B4FE), Color(0xD9FFFFFF))
    )

    if (!isExpanded) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xD9253452), Color(0xC80B1324))))
                .border(1.3.dp, glassRim, CircleShape)
                .pointerInteropFilter { event ->
                    onBallTouch(event) {
                        isExpanded = true
                        onExpandedChanged(true)
                        PriceBus.requestScanNow()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                    .width(22.dp).height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.24f))
            )
            Text("¥", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(8.dp).clip(CircleShape)
                    .background(if (state.isAccessibilityConnected) Color(0xFF34D399) else Color(0xFFFBBF24))
            )
        }
    } else {
        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .width(236.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xD9152238), Color(0xCC0A1120), Color(0xD41B2945))))
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.Transparent), endY = size.height * 0.32f)
                    )
                    val p = sweepProgress.value
                    if (p > -0.35f && p < 1.45f) {
                        val x = size.width * p
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(0.32f), Color.Transparent),
                                Offset(x - 70f, 0f), Offset(x + 70f, size.height)
                            )
                        )
                    }
                }
                .border(1.2.dp, glassRim, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp).height(22.dp)
                        .pointerInteropFilter { onPillDragTouch(it) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(26.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(0.45f)))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniGlassBtn(text = state.currency.label) {
                        val next = if (state.currency == YenCurrency.JPY) YenCurrency.CNY else YenCurrency.JPY
                        PriceBus.setCurrency(context, next)
                    }
                    MiniGlassBtn(text = "↻", active = state.isScanning) { PriceBus.requestScanNow() }
                    MiniGlassBtn(text = "⚙", active = isEditingRate) {
                        isEditingRate = !isEditingRate
                        rateDraft = formatDec(state.activeRate)
                    }
                    MiniGlassBtn(text = "✕") {
                        isEditingRate = false
                        onCloseOverlay()
                    }
                }
            }

            if (!state.isAccessibilityConnected) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color(0x55F59E0B)).border(0.8.dp, Color(0xCCFCD34D), RoundedCornerShape(10.dp))
                        .clickable {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("⚠️ Auto-Scan ဖွင့်ရန် နှိပ်ပါ", color = Color(0xFFFEF3C7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0x80060B17))
                    .border(0.8.dp, Color.White.copy(0.18f), RoundedCornerShape(15.dp))
                    .clickable {
                        isEditingRate = false
                        isExpanded = false
                        onExpandedChanged(false)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            state.isScanning -> "ဖတ်နေသည်..."
                            state.statusText.isNotEmpty() && state.detectedAmount == 0.0 -> state.statusText
                            else -> "¥${formatDec(state.detectedAmount)}"
                        },
                        color = Color(0xFF7DD3FC),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "1¥=${formatDec(state.activeRate)}",
                        color = Color.White.copy(0.55f),
                        fontSize = 9.sp
                    )
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = popScale.value; scaleY = popScale.value },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formatMyanmarLakh(animatedMmk.toDouble()),
                        color = Color(0xFF6EE7B7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatInt(animatedMmk), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(3.dp))
                        Text("ကျပ်", color = Color(0xFFBAE6FD), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }

            if (state.detectedPrices.size > 1) {
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.detectedPrices.forEach { price ->
                        val active = price == state.detectedAmount
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0x8038BDF8) else Color.White.copy(0.14f))
                                .clickable { PriceBus.selectPrice(price) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("¥${formatDec(price)}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isEditingRate, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    BasicTextField(
                        value = rateDraft,
                        onValueChange = { rateDraft = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Color(0xFF7DD3FC)),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(Color(0x88060B17))
                            .border(1.dp, Color(0x997DD3FC), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8))))
                            .clickable {
                                rateDraft.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                                    PriceBus.updateRate(context, state.currency, it)
                                }
                                isEditingRate = false
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("သိမ်း", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniGlassBtn(text: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Color(0x6638BDF8) else Color.White.copy(0.16f))
            .border(0.7.dp, if (active) Color(0xCC7DD3FC) else Color.White.copy(0.28f), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatInt(value: Int): String = NumberFormat.getNumberInstance(Locale.US).format(value)

private fun formatDec(value: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = 2
    return nf.format(value)
}

private fun formatMyanmarLakh(mmk: Double): String = when {
    mmk <= 0 -> "0 ကျပ်"
    mmk >= 100_000 -> String.format(Locale.US, "≈ %.2f သိန်း", mmk / 100_000.0)
    mmk >= 10_000 -> String.format(Locale.US, "≈ %.2f သောင်း", mmk / 10_000.0)
    else -> ""
}
