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
        private const val KEY_BLACKLIST_DOMAINS = "key_blacklist"
        private const val KEY_BLOCKED_APPS = "key_blocked_apps"
        private const val KEY_BLOCK_COUNT = "key_block_count"
        private const val KEY_STREAK_START = "key_streak_start"

        // Lista por defecto de dominios y palabras clave para adultos
        val DEFAULT_ADULT_KEYWORDS = setOf(
            "porno", "porn", "xxx", "xvideos", "xnxx", "pornhub",
            "redtube", "erotic", "hentai", "onlyfans", "chaturbate",
            "stripchat", "cam4", "brazzers", "youporn", "spankbang",
            "xhamster", "rule34", "nude", "tits", "sexo", "erotico",
            "sex", "sexy", "boobs", "milf", "nsfw"
        )

        val DEFAULT_BLACKLIST_DOMAINS = setOf(
            "x.com", "twitter.com"
        )

        // Paquetes y nombres de apps bloqueadas por defecto
        val DEFAULT_BLOCKED_APPS = setOf(
            "com.twitter.android",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill"
        )

        // Palabras clave en Play Store para apps bloqueadas
        val DEFAULT_PLAYSTORE_APP_KEYWORDS = mapOf(
            "com.twitter.android" to listOf("x", "twitter"),
            "com.instagram.android" to listOf("instagram"),
            "com.zhiliaoapp.musically" to listOf("tiktok"),
            "com.reddit.frontpage" to listOf("reddit")
        )
    }

    // --- PIN Management (6 a 8 dígitos) ---
    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val hash = SecurityUtils.sha256(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == SecurityUtils.sha256(pin)
    }

    // --- Protection Master Switch ---
    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        set(value) {
            if (value && !isProtectionEnabled) {
                // Al reactivar, inicia racha
                if (streakStartTimestamp == 0L) {
                    streakStartTimestamp = System.currentTimeMillis()
                }
            }
            prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()
        }

    // --- Adult Filter ---
    var isAdultBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADULT_BLOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ADULT_BLOCK_ENABLED, value).apply()

    // --- Incognito Filter ---
    var isIncognitoBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO_BLOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_INCOGNITO_BLOCK_ENABLED, value).apply()

    // --- Custom Blacklist Domains ---
    fun getBlacklist(): Set<String> {
        return prefs.getStringSet(KEY_BLACKLIST_DOMAINS, null) ?: DEFAULT_BLACKLIST_DOMAINS
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
            prefs.edit().putStringSet(KEY_BLACKLIST_DOMAINS, current).apply()
        }
    }

    fun removeBlacklistDomain(domain: String) {
        val current = getBlacklist().toMutableSet()
        current.remove(domain)
        prefs.edit().putStringSet(KEY_BLACKLIST_DOMAINS, current).apply()
    }

    // --- Blocked Apps Management ---
    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_APPS, null) ?: DEFAULT_BLOCKED_APPS
    }

    fun addBlockedApp(pkgOrKeyword: String) {
        val clean = pkgOrKeyword.trim().lowercase()
        if (clean.isNotEmpty()) {
            val current = getBlockedApps().toMutableSet()
            current.add(clean)
            prefs.edit().putStringSet(KEY_BLOCKED_APPS, current).apply()
        }
    }

    fun removeBlockedApp(pkgOrKeyword: String) {
        val current = getBlockedApps().toMutableSet()
        current.remove(pkgOrKeyword)
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, current).apply()
    }

    // --- Stats: Contador de Bloqueos y Racha ---
    var blockCount: Int
        get() = prefs.getInt(KEY_BLOCK_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_BLOCK_COUNT, value).apply()

    fun incrementBlockCount() {
        blockCount += 1
    }

    var streakStartTimestamp: Long
        get() = prefs.getLong(KEY_STREAK_START, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_STREAK_START, value).apply()

    val streakDays: Int
        get() {
            val elapsed = System.currentTimeMillis() - streakStartTimestamp
            val days = (elapsed / (1000 * 60 * 60 * 24)).toInt()
            return if (days < 1) 1 else days
        }
}
