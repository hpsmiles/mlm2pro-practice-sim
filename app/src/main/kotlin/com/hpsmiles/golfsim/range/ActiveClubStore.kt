package com.hpsmiles.golfsim.range

import android.content.Context
import android.content.SharedPreferences

/**
 * The active-club pill selection (spec §4 — SharedPreferences alongside the
 * SecretStore pattern; no DataStore migration, requirements did not grow).
 */
class ActiveClubStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString(KEY, null)

    fun set(clubName: String?) {
        prefs.edit().putString(KEY, clubName).apply()
    }

    private companion object {
        const val PREFS_FILE = "golfsim_prefs"
        const val KEY = "active_club"
    }
}
