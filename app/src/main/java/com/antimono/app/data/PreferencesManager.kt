package com.antimono.app.data

import android.content.Context
import android.content.SharedPreferences
import com.antimono.app.security.SecurityUtils

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("antimono_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_PROTECTION_ENABLED = "key_protection_enabled"
        private const val KEY_ADULT_BLOCK_ENABLED = "key_adult_block_enabled"
        private const val KEY_INCOGNITO_BLOCK_ENABLED = "key_incognito_block_enabled"
        private const val KEY_BLACKLIST = "key_blacklist"
        private const val KEY_TEMP_UNLOCK_UNTIL = "key_temp_unlock_until"

        // Lista por defecto de dominios y palabras clave para adultos
        val DEFAULT_ADULT_KEYWORDS = setOf(
            "porno", "porn", "xxx", "xvideos", "xnxx", "pornhub",
            "redtube", "erotic", "hentai", "onlyfans", "chaturbate",
            "stripchat", "cam4", "brazzers", "youporn", "spankbang",
            "xhamster", "rule34", "nude", "tits", "sexo", "erotico"
        )

        val DEFAULT_BLACKLIST = setOf(
            "x.com", "twitter.com"
        )
    }

    // --- PIN Management ---
    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val hash = SecurityUtils.sha256(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == SecurityUtils.sha256(pin)
    }

    // --- Temporary Unlock Window (permite entrar a ajustes de la app sin re-bloquearse) ---
    fun setTempUnlock(durationMinutes: Int = 2) {
        val until = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        prefs.edit().putLong(KEY_TEMP_UNLOCK_UNTIL, until).apply()
    }

    fun isTempUnlocked(): Boolean {
        val until = prefs.getLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun clearTempUnlock() {
        prefs.edit().remove(KEY_TEMP_UNLOCK_UNTIL).apply()
    }

    // --- Protection Master Switch ---
    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    // --- Adult Filter ---
    var isAdultBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADULT_BLOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ADULT_BLOCK_ENABLED, value).apply()

    // --- Incognito Filter ---
    var isIncognitoBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO_BLOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_INCOGNITO_BLOCK_ENABLED, value).apply()

    // --- Custom Blacklist ---
    fun getBlacklist(): Set<String> {
        return prefs.getStringSet(KEY_BLACKLIST, null) ?: DEFAULT_BLACKLIST
    }

    fun addBlacklistDomain(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
        if (clean.isNotEmpty()) {
            val current = getBlacklist().toMutableSet()
            current.add(clean)
            prefs.edit().putStringSet(KEY_BLACKLIST, current).apply()
        }
    }

    fun removeBlacklistDomain(domain: String) {
        val current = getBlacklist().toMutableSet()
        current.remove(domain)
        prefs.edit().putStringSet(KEY_BLACKLIST, current).apply()
    }
}
