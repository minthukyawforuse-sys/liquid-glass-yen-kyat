package com.liquidglass.yenkyat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class YenCurrency(val code: String, val label: String, val bulkUnit: Int, val defaultRate: Double) {
    JPY("JPY", "🇯🇵 ဂျပန်ယန်း", 10_000, 28.5),
    CNY("CNY", "🇨🇳 တရုတ်ယွမ်", 100, 620.0)
}

data class ConverterState(
    val currency: YenCurrency = YenCurrency.JPY,
    val jpyRate: Double = YenCurrency.JPY.defaultRate,
    val cnyRate: Double = YenCurrency.CNY.defaultRate,
    val detectedAmount: Double = 0.0,
    val detectedPrices: List<Double> = emptyList(),
    val sourceLabel: String = "Screen ပေါ်က ¥ ဈေးနှုန်း စောင့်ကြည့်နေသည်...",
    val autoScanEnabled: Boolean = true,
    val isOverlayRunning: Boolean = false
) {
    val activeRate: Double
        get() = if (currency == YenCurrency.JPY) jpyRate else cnyRate

    val convertedMmk: Double
        get() = detectedAmount * activeRate
}

object PriceBus {
    private const val PREFS_NAME = "liquid_glass_yen_kyat_prefs"
    private const val KEY_JPY_RATE = "jpy_rate"
    private const val KEY_CNY_RATE = "cny_rate"
    private const val KEY_CURRENCY = "currency"

    private val _state = MutableStateFlow(ConverterState())
    val state: StateFlow<ConverterState> = _state.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJpy = prefs.getFloat(KEY_JPY_RATE, YenCurrency.JPY.defaultRate.toFloat()).toDouble()
        val savedCny = prefs.getFloat(KEY_CNY_RATE, YenCurrency.CNY.defaultRate.toFloat()).toDouble()
        val savedCurrName = prefs.getString(KEY_CURRENCY, YenCurrency.JPY.name)
        val currency = runCatching { YenCurrency.valueOf(savedCurrName ?: "JPY") }
            .getOrDefault(YenCurrency.JPY)

        _state.update {
            it.copy(
                currency = currency,
                jpyRate = if (savedJpy > 0) savedJpy else YenCurrency.JPY.defaultRate,
                cnyRate = if (savedCny > 0) savedCny else YenCurrency.CNY.defaultRate
            )
        }
    }

    fun updateDetectedPrices(prices: List<Double>, sourceApp: String) {
        if (!_state.value.autoScanEnabled || prices.isEmpty()) return
        val primary = prices.first()
        _state.update { current ->
            if (current.detectedAmount == primary && current.detectedPrices == prices) {
                current
            } else {
                current.copy(
                    detectedAmount = primary,
                    detectedPrices = prices,
                    sourceLabel = sourceApp
                )
            }
        }
    }

    fun selectManualPrice(amount: Double, label: String = "ရွေးချယ်ထားသော ဈေးနှုန်း") {
        _state.update {
            it.copy(
                detectedAmount = amount.coerceAtLeast(0.0),
                sourceLabel = label
            )
        }
    }

    fun setCurrency(context: Context, currency: YenCurrency) {
        _state.update { it.copy(currency = currency) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENCY, currency.name)
            .apply()
    }

    fun updateRate(context: Context, currency: YenCurrency, newRate: Double) {
        if (newRate <= 0.0) return
        _state.update {
            if (currency == YenCurrency.JPY) it.copy(jpyRate = newRate)
            else it.copy(cnyRate = newRate)
        }
        val key = if (currency == YenCurrency.JPY) KEY_JPY_RATE else KEY_CNY_RATE
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(key, newRate.toFloat())
            .apply()
    }

    fun toggleAutoScan() {
        _state.update { it.copy(autoScanEnabled = !it.autoScanEnabled) }
    }

    fun setOverlayRunning(running: Boolean) {
        _state.update { it.copy(isOverlayRunning = running) }
    }
}
