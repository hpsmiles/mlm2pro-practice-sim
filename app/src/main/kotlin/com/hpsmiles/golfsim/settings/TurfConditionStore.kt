package com.hpsmiles.golfsim.settings

import android.content.Context
import android.content.SharedPreferences
import com.hpsmiles.golfsim.core.physics.TurfCondition

/**
 * User-selected off-green turf (Settings > TURF). SharedPreferences alongside
 * the SecretStore / ActiveClubStore / GreenConditionStore pattern.
 */
class TurfConditionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun get(): TurfCondition = TurfCondition.fromName(prefs.getString(KEY, null))

    fun set(condition: TurfCondition) {
        prefs.edit().putString(KEY, condition.name).apply()
    }

    private companion object {
        const val PREFS_FILE = "golfsim_prefs"
        const val KEY = "turf_condition"
    }
}
