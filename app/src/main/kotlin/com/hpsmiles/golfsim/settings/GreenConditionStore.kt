package com.hpsmiles.golfsim.settings

import android.content.Context
import android.content.SharedPreferences
import com.hpsmiles.golfsim.core.physics.GreenCondition

/**
 * User-selected green condition (Settings > GREEN). SharedPreferences alongside
 * the SecretStore / ActiveClubStore pattern — a single enum name, so DataStore
 * is unnecessary (requirements did not grow).
 */
class GreenConditionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun get(): GreenCondition = GreenCondition.fromName(prefs.getString(KEY, null))

    fun set(condition: GreenCondition) {
        prefs.edit().putString(KEY, condition.name).apply()
    }

    private companion object {
        const val PREFS_FILE = "golfsim_prefs"
        const val KEY = "green_condition"
    }
}
