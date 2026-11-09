package com.antimono.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.antimono.app.data.PreferencesManager
import com.antimono.app.ui.BlockOverlayActivity

class AntiMonoAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferencesManager

    // Paquetes de navegadores conocidos (incluyendo visor de Google y navegadores secundarios)
    private val browserPackages = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.duckduckgo.mobile.android",
        "com.google.android.googlequicksearchbox",
        "com.kiwibrowser.browser",
        "org.torproject.torbrowser",
        "com.aloha.browser",
        "com.vivaldi.browser",
        "mark.via.gp",
        "com.ucmobile.intl"
    )

    // Tiendas de aplicaciones (Google Play Store y Samsung Galaxy Store)
    private val appStorePackages = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps"
    )

    // Ajustes y desinstalador del sistema
    private val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    // Carpeta Segura de Samsung (Knox Secure Folder)
    private val secureFolderPackages = setOf(
        "com.samsung.knox.securefolder",
        "com.sec.knox.switcher"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Si el interruptor maestro está desactivado con el PIN, la app es completamente invisible
        if (!prefs.isProtectionEnabled) return

        val packageName = event?.packageName?.toString() ?: ""

        // No interferir con la propia app AntiMono
        if (packageName == applicationContext.packageName) return

        // 1. CENTINELA DE TECLADO: Limpieza de palabras en tiempo real
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            handleLiveTextInput(event)
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val activePkg = packageName.ifEmpty { rootNode.packageName?.toString() ?: "" }

        // 2. PARCHE 1: Bloquear Carpeta Segura de Samsung
        if (secureFolderPackages.contains(activePkg) || activePkg.contains("securefolder")) {
            blockCurrentItem("Función Restringida", "El acceso a Carpeta Segura está bloqueado.")
            return
        }

        // 3. PARCHE 2: Proteger Ajustes del sistema (Regla de Oro: Nadie toca AntiMono en Ajustes)
        if (settingsPackages.contains(activePkg)) {
            handleSettingsProtection(rootNode)
            return
        }

        // 4. PARCHE 3: Tiendas de Aplicaciones (Google Play y Samsung Galaxy Store)
        if (prefs.isAppBlockEnabled && appStorePackages.contains(activePkg)) {
            handleAppStoreInspection(rootNode)
            return
        }

        // 5. PARCHE 4: Bloquear aplicaciones prohibidas instaladas
        if (prefs.isAppBlockEnabled && handleInstalledAppBlock(activePkg)) {
            return
        }

        // 6. PARCHE 5: Filtrado de navegadores web y visor de Google
        if (browserPackages.contains(activePkg)) {
            handleBrowserInspection(rootNode)
        }
    }

    /**
     * Centinela de Teclado: Detecta palabras restringidas y limpia el campo al instante
     * en las aplicaciones seleccionadas por el usuario.
     */
    private fun handleLiveTextInput(event: AccessibilityEvent) {
        if (!prefs.isKeywordGuardianEnabled) return

        val sourceNode = event.source ?: return
        val activePkg = event.packageName?.toString() ?: sourceNode.packageName?.toString() ?: ""

        // Verificar si la aplicación actual está vigilada
        val monitoredApps = prefs.getMonitoredKeyboardApps()
        val isMonitored = monitoredApps.contains(activePkg) ||
                monitoredApps.any { activePkg.contains(it) } ||
                browserPackages.contains(activePkg)

        if (!isMonitored) return

        val eventTexts = event.text
        val combinedText = eventTexts.joinToString(" ").lowercase()
            .ifBlank { sourceNode.text?.toString()?.lowercase() ?: "" }

        if (combinedText.isBlank()) return

        val prohibitedWords = prefs.getProhibitedKeywords()
        for (keyword in prohibitedWords) {
            if (containsIsolatedKeyword(combinedText, keyword)) {
                val clearBundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
                val cleared = sourceNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearBundle)

                if (cleared) {
                    triggerKeyboardHaptic()
                    Toast.makeText(this, "Restricción activa: \"$keyword\"", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    /**
     * Revisa si la app abierta está en la lista de aplicaciones bloqueadas
     */
    private fun handleInstalledAppBlock(packageName: String): Boolean {
        val blockedApps = prefs.getBlockedApps()
        val pkgLower = packageName.lowercase()

        for (blocked in blockedApps) {
            if (pkgLower == blocked || pkgLower.contains(blocked)) {
                blockCurrentItem("Aplicación Restringida", "Esta aplicación no está permitida.")
                return true
            }
        }
        return false
    }

    /**
     * Inspecciona Google Play Store y Samsung Galaxy Store para impedir descargas
     */
    private fun handleAppStoreInspection(rootNode: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectAllText(rootNode, allTexts)
        val combinedText = allTexts.joinToString(" ").lowercase()

        val blockedKeywords = setOf(
            "twitter", "tiktok", "instagram", "reddit", "telegram",
            "viber", "line", "discord", "tinder", "bumble", "badoo",
            "ometv", "browser", "navegador", "vpn", "proxy"
        )

        var foundRestricted = false
        val blockedApps = prefs.getBlockedApps()
        for (blocked in blockedApps) {
            if (combinedText.contains(blocked)) {
                foundRestricted = true
                break
            }
        }
        if (!foundRestricted) {
            for (kw in blockedKeywords) {
                if (combinedText.contains(kw)) {
                    foundRestricted = true
                    break
                }
            }
        }

        // Si se encuentra en la ficha o frente a botones de descarga
        val hasInstallAction = combinedText.contains("instalar") ||
                combinedText.contains("install") ||
                combinedText.contains("actualizar") ||
                combinedText.contains("update") ||
                combinedText.contains("descargar") ||
                combinedText.contains("download") ||
                combinedText.contains("abrir") ||
                combinedText.contains("open")

        if (foundRestricted && hasInstallAction) {
            blockCurrentItem(
                "Descarga Bloqueada",
                "AntiMono impide la descarga de aplicaciones restringidas."
            )
        }
    }

    /**
     * REGLA DE ORO DE AJUSTES: Cualquier pantalla en Ajustes que mencione AntiMono se expulsa de golpe,
     * pero se PERMITE expresamente cuando el instalador del paquete está actualizando la aplicación.
     */
    private fun handleSettingsProtection(rootNode: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectAllText(rootNode, allTexts)
        val combinedText = allTexts.joinToString(" ").lowercase()

        // 1. Si es el instalador de paquetes actualizando AntiMono, PERMITIR sin bloquear
        val isUpdateOrInstall = combinedText.contains("actualizar") ||
                combinedText.contains("update") ||
                combinedText.contains("actualización") ||
                (combinedText.contains("instalar") && !combinedText.contains("desinstalar"))
        if (isUpdateOrInstall) {
            return
        }

        // 2. Si la pantalla menciona desinstalar AntiMono o alterar Ajustes/Accesibilidad
        val mentionsAntiMono = combinedText.contains("antimono")

        val isDeviceAdminScreen = combinedText.contains("administrador de dispositivos") ||
                combinedText.contains("device admin") ||
                combinedText.contains("apps de administración")

        val isDeveloperTampering = combinedText.contains("opciones de desarrollador") ||
                combinedText.contains("developer options") ||
                combinedText.contains("depuración por usb") ||
                combinedText.contains("usb debugging") ||
                combinedText.contains("número de compilación") ||
                combinedText.contains("build number")

        val isSafeModeTampering = combinedText.contains("modo seguro") ||
                combinedText.contains("safe mode")

        if (mentionsAntiMono || isDeviceAdminScreen || isDeveloperTampering || isSafeModeTampering) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(
                this,
                "Ajustes protegidos: Para modificar permisos desactiva la protección en AntiMono.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Inspecciona la pantalla de navegadores web para detectar incógnito y URLs restringidas
     */
    private fun handleBrowserInspection(rootNode: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectAllText(rootNode, allTexts)

        for (text in allTexts) {
            val lower = text.lowercase()

            // A) Detección de Modo Incógnito / Pestaña Secreta
            if (prefs.isIncognitoBlockEnabled) {
                if (lower.contains("incógnito") || lower.contains("incognito") ||
                    lower.contains("pestaña secreta") || lower.contains("modo secreto")
                ) {
                    blockCurrentItem("Navegación Privada Restringida", "Esta función no está permitida.")
                    return
                }
            }

            // B) Detección de Lista Negra personalizada (ej. x.com, twitter.com)
            if (prefs.isWebBlockEnabled) {
                val blacklist = prefs.getBlacklist()
                for (domain in blacklist) {
                    if (lower.contains(domain)) {
                        blockCurrentItem("Sitio Restringido", "Dominio bloqueado: $domain")
                        return
                    }
                }
            }

            // C) Detección de Palabras Prohibidas y NSFW
            if (prefs.isAdultBlockEnabled) {
                for (keyword in prefs.getProhibitedKeywords()) {
                    if (containsIsolatedKeyword(lower, keyword)) {
                        blockCurrentItem("Contenido Restringido", "Filtro activo para: $keyword")
                        return
                    }
                }
            }
        }
    }

    private fun containsIsolatedKeyword(text: String, keyword: String): Boolean {
        if (!text.contains(keyword)) return false
        val regex = Regex("""(?i)(?:^|[\W_])${Regex.escape(keyword)}""")
        return regex.containsMatchIn(text)
    }

    private fun blockCurrentItem(title: String, reason: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_REASON", reason)
        }
        startActivity(intent)
    }

    private fun triggerKeyboardHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(80)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return

        node.text?.let { if (it.isNotBlank()) list.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotBlank()) list.add(it.toString()) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectAllText(child, list)
        }
    }

    override fun onInterrupt() {}
}
