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
        private const val KEY_KEYWORD_GUARDIAN_ENABLED = "key_keyword_guardian_enabled"
        private const val KEY_MONITORED_KEYBOARD_APPS = "key_monitored_keyboard_apps"

        // Apps vigiladas por el Centinela de Teclado por defecto (WhatsApp EXCLUIDO por privacidad)
        val DEFAULT_MONITORED_KEYBOARD_APPS = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.instagram.android",
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "com.google.android.youtube",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.reddit.frontpage"
        )

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
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

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

    // --- Centinela de Teclado (Filtro selectivo por Apps) ---
    var isKeywordGuardianEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEYWORD_GUARDIAN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_KEYWORD_GUARDIAN_ENABLED, value).apply()

    fun getMonitoredKeyboardApps(): Set<String> {
        return prefs.getStringSet(KEY_MONITORED_KEYBOARD_APPS, null) ?: DEFAULT_MONITORED_KEYBOARD_APPS
    }

    fun setMonitoredKeyboardApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_MONITORED_KEYBOARD_APPS, apps).apply()
    }

    fun addMonitoredKeyboardApp(packageName: String) {
        val current = getMonitoredKeyboardApps().toMutableSet()
        current.add(packageName)
        setMonitoredKeyboardApps(current)
    }

    fun removeMonitoredKeyboardApp(packageName: String) {
        val current = getMonitoredKeyboardApps().toMutableSet()
        current.remove(packageName)
        setMonitoredKeyboardApps(current)
    }
}
