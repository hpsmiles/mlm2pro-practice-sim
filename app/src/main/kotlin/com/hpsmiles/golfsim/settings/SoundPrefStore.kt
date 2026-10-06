package com.hpsmiles.golfsim.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Sounds on/off (Settings > SOUND). SharedPreferences alongside the other
 * single-value stores (GreenConditionStore pattern) — DataStore unnecessary.
 */
class SoundPrefStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun get(): Boolean = prefs.getBoolean(KEY, true)

    fun set(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    private companion object {
        const val PREFS_FILE = "golfsim_prefs"
        const val KEY = "sounds_enabled"
    }
}
