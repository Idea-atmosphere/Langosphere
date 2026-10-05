package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

/**
 * Weight choices for the source (original) and target (translation) languages.
 * Each side can be Regular, Medium, Bold, or Black — the four weights the
 * user asked for. Persisted in `ai_prefs` alongside the language pair itself.
 */
enum class LanguageWeight(val key: String, val weight: FontWeight) {
    REGULAR("regular", FontWeight.Normal),
    MEDIUM("medium", FontWeight.Medium),
    BOLD("bold", FontWeight.Bold),
    BLACK("black", FontWeight.Black);

    companion object {
        fun fromKey(key: String?): LanguageWeight =
            values().firstOrNull { it.key == key } ?: REGULAR

        fun label(weight: LanguageWeight, fa: Boolean): String = when (weight) {
            REGULAR -> if (fa) "معمولی" else "Regular"
            MEDIUM -> if (fa) "متوسط" else "Medium"
            BOLD -> if (fa) "ضخیم" else "Bold"
            BLACK -> if (fa) "خیلی ضخیم" else "Black"
        }
    }
}

object LanguageWeightState {
    private const val PREFS_NAME = "ai_prefs"
    private const val KEY_SOURCE_WEIGHT = "source_weight"
    private const val KEY_TARGET_WEIGHT = "target_weight"

    var sourceWeight by mutableStateOf(LanguageWeight.REGULAR)
        private set
    var targetWeight by mutableStateOf(LanguageWeight.REGULAR)
        private set

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sourceWeight = LanguageWeight.fromKey(prefs.getString(KEY_SOURCE_WEIGHT, null))
        targetWeight = LanguageWeight.fromKey(prefs.getString(KEY_TARGET_WEIGHT, null))
    }

    fun setSourceWeight(context: Context, weight: LanguageWeight) {
        sourceWeight = weight
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SOURCE_WEIGHT, weight.key).apply()
    }

    fun setTargetWeight(context: Context, weight: LanguageWeight) {
        targetWeight = weight
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TARGET_WEIGHT, weight.key).apply()
    }
}
