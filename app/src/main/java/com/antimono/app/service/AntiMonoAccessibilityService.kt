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

    // Paquetes de navegadores conocidos
    private val browserPackages = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android"
    )

    // Paquetes de ajustes y desinstalador del sistema
    private val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    private val playStorePackage = "com.android.vending"

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Si el interruptor maestro está apagado, la app es completamente invisible
        if (!prefs.isProtectionEnabled) return

        val packageName = event?.packageName?.toString() ?: ""

        // No interferir con la propia app AntiMono
        if (packageName == applicationContext.packageName) return

        // 1. CENTINELA DE TECLADO: Borrado automático de palabras prohibidas en tiempo real
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            handleLiveTextInput(event)
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val activePkg = packageName.ifEmpty { rootNode.packageName?.toString() ?: "" }

        // 2. Proteger contra desinstalación o alteración de permisos
        if (settingsPackages.contains(activePkg)) {
            handleSettingsProtection(rootNode)
            return
        }

        // 3. Bloquear Google Play Store si intentan descargar una app restringida
        if (activePkg == playStorePackage) {
            handlePlayStoreInspection(rootNode)
            return
        }

        // 4. Bloquear APPS instaladas que estén en la lista negra
        if (handleInstalledAppBlock(activePkg)) {
            return
        }

        // 5. Filtrado de navegadores web
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

        // Verificar si la aplicación actual está en la lista de apps vigiladas o es un navegador
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

    /**
     * Revisa si la app abierta está en la lista de aplicaciones bloqueadas
     */
    private fun handleInstalledAppBlock(packageName: String): Boolean {
        val blockedApps = prefs.getBlockedApps()
        val pkgLower = packageName.lowercase()

        for (blocked in blockedApps) {
            if (pkgLower == blocked || pkgLower.contains(blocked)) {
                blockCurrentItem("App Bloqueada", "El changuito neutralizó esta aplicación.")
                return true
            }
        }
        return false
    }

    /**
     * Inspecciona la Google Play Store para impedir descargar apps prohibidas
     */
    private fun handlePlayStoreInspection(rootNode: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectAllText(rootNode, allTexts)
        val combinedText = allTexts.joinToString(" ").lowercase()

        val blockedApps = prefs.getBlockedApps()
        val dangerousKeywords = setOf("twitter", "x.com", "tiktok", "instagram", "reddit")

        var foundBlockedApp = false
        for (blocked in blockedApps) {
            if (combinedText.contains(blocked)) {
                foundBlockedApp = true
                break
            }
        }
        if (!foundBlockedApp) {
            for (kw in dangerousKeywords) {
                if (combinedText.contains(kw)) {
                    foundBlockedApp = true
                    break
                }
            }
        }

        // Si está en la página de la app prohibida o frente al botón de instalar
        val hasInstallAction = combinedText.contains("instalar") ||
                combinedText.contains("install") ||
                combinedText.contains("actualizar") ||
                combinedText.contains("update") ||
                combinedText.contains("habilitar") ||
                combinedText.contains("enable")

        if (foundBlockedApp && hasInstallAction) {
            blockCurrentItem(
                "Descarga Bloqueada",
                "AntiMono te impide descargar aplicaciones no deseadas de la Play Store."
            )
        }
    }

    /**
     * Inspecciona pantallas de Ajustes/Desinstalador para evitar que quiten permisos
     */
    private fun handleSettingsProtection(rootNode: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectAllText(rootNode, allTexts)
        val combinedText = allTexts.joinToString(" ").lowercase()

        // Si la pantalla menciona AntiMono Y acciones de desinstalación o quitar permisos
        val mentionsAntiMono = combinedText.contains("antimono")
        val mentionsDangerousAction = combinedText.contains("desinstalar") ||
                combinedText.contains("uninstall") ||
                combinedText.contains("desactivar") ||
                combinedText.contains("deactivate") ||
                combinedText.contains("borrar datos") ||
                combinedText.contains("clear data") ||
                combinedText.contains("forzar detención") ||
                combinedText.contains("force stop")

        val isDeviceAdminScreen = combinedText.contains("administrador de dispositivos") ||
                combinedText.contains("device admin") ||
                combinedText.contains("apps de administración")

        // BLINDAJE ANTI-YO: Prohibir reactivar opciones de desarrollador o depuración USB
        val isDeveloperTampering = combinedText.contains("opciones de desarrollador") ||
                combinedText.contains("developer options") ||
                combinedText.contains("depuración por usb") ||
                combinedText.contains("usb debugging") ||
                combinedText.contains("número de compilación") ||
                combinedText.contains("build number")

        // BLINDAJE: Prohibir acceso a reiniciar en Modo Seguro
        val isSafeModeTampering = combinedText.contains("modo seguro") ||
                combinedText.contains("safe mode")

        if ((mentionsAntiMono && mentionsDangerousAction) ||
            (mentionsAntiMono && isDeviceAdminScreen) ||
            isDeveloperTampering ||
            isSafeModeTampering
        ) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(
                this,
                "Acción bloqueada: Para modificar permisos desactiva el interruptor en AntiMono.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Inspecciona la pantalla del navegador para detectar pestañas de incógnito y URLs bloqueadas
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
                    blockCurrentItem("Modo Incógnito no permitido", "Pestaña secreta cerrada.")
                    return
                }
            }

            // B) Detección de Lista Negra personalizada (ej. x.com, twitter.com)
            val blacklist = prefs.getBlacklist()
            for (domain in blacklist) {
                if (lower.contains(domain)) {
                    blockCurrentItem("Sitio Restringido", "Dominio bloqueado: $domain")
                    return
                }
            }

            // C) Detección de Contenido para Adultos (Palabras Prohibidas)
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
        val regex = Regex("""(?i)(?:^|[\W_])${Regex.escape(keyword)}(?:[\W_]|$)""")
        return regex.containsMatchIn(text)
    }

    /**
     * Acción de bloqueo: expulsa a la pantalla de inicio y muestra la pantalla interactiva
     */
    private fun blockCurrentItem(title: String, reason: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_REASON", reason)
        }
        startActivity(intent)
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
