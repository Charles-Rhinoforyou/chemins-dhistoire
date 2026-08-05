package com.cheminsdhistoire.app.data

import android.content.Context

/** Préférences locales (clé API Gemini, choix du moteur). Rien n'est envoyé ailleurs. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("chemins_settings", Context.MODE_PRIVATE)

    var geminiKey: String
        get() = prefs.getString(KEY_GEMINI, "") ?: ""
        set(v) = prefs.edit().putString(KEY_GEMINI, v.trim()).apply()

    var useGemini: Boolean
        get() = prefs.getBoolean(KEY_USE_GEMINI, false)
        set(v) = prefs.edit().putBoolean(KEY_USE_GEMINI, v).apply()

    var useLlm: Boolean
        get() = prefs.getBoolean(KEY_USE_LLM, false)
        set(v) = prefs.edit().putBoolean(KEY_USE_LLM, v).apply()

    var useGeminiVoice: Boolean
        get() = prefs.getBoolean(KEY_USE_GEMINI_VOICE, false)
        set(v) = prefs.edit().putBoolean(KEY_USE_GEMINI_VOICE, v).apply()

    companion object {
        private const val KEY_GEMINI = "gemini_key"
        private const val KEY_USE_GEMINI = "use_gemini"
        private const val KEY_USE_LLM = "use_llm"
        private const val KEY_USE_GEMINI_VOICE = "use_gemini_voice"
    }
}
