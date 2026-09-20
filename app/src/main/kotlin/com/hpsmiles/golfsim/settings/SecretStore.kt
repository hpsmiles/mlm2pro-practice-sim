// app/src/main/kotlin/com/hpsmiles/golfsim/settings/SecretStore.kt
package com.hpsmiles.golfsim.settings

import android.content.Context

/**
 * Stores the Rapsodo user Secret (for the Phase B token fetch).
 * SharedPreferences is sufficient for one string; migrate to DataStore
 * in M5 persistence if requirements grow.
 */
class SecretStore(context: Context) {

    private val prefs = context.getSharedPreferences("golfsim_prefs", Context.MODE_PRIVATE)

    fun getSecret(): String = prefs.getString(KEY, "").orEmpty()

    fun setSecret(secret: String) {
        prefs.edit().putString(KEY, secret).apply()
    }

    companion object {
        private const val KEY = "rapsodo_secret"
    }
}
