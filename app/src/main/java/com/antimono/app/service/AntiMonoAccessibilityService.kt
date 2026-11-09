package com.antimono.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!prefs.isProtectionEnabled) return

        val rootNode = rootInActiveWindow ?: return
        val packageName = event?.packageName?.toString() ?: rootNode.packageName?.toString() ?: ""

        // 1. Proteger contra desinstalación o alteración de permisos
        if (settingsPackages.contains(packageName)) {
            handleSettingsProtection(rootNode)
            return
        }

        // 2. Filtrado de navegadores web
        if (browserPackages.contains(packageName)) {
            handleBrowserInspection(rootNode)
        }
    }

    /**
     * Inspecciona pantallas de Ajustes/Desinstalador para evitar que quiten permisos sin PIN
     */
    private fun handleSettingsProtection(rootNode: AccessibilityNodeInfo) {
        if (prefs.isTempUnlocked()) {
            // El usuario puso su PIN en la app recientemente y tiene permiso temporal
            return
        }

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

        if ((mentionsAntiMono && mentionsDangerousAction) || (mentionsAntiMono && isDeviceAdminScreen)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(
                this,
                "Acceso restringido: Ingresa tu PIN en AntiMono para modificar estos permisos.",
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
                    blockCurrentPage("Modo Incógnito no permitido")
                    return
                }
            }

            // B) Detección de Lista Negra personalizada (ej. x.com, twitter.com)
            val blacklist = prefs.getBlacklist()
            for (domain in blacklist) {
                if (lower.contains(domain)) {
                    blockCurrentPage("Sitio restringido por lista negra: $domain")
                    return
                }
            }

            // C) Detección de Contenido para Adultos (Porno / NSFW)
            if (prefs.isAdultBlockEnabled) {
                for (keyword in PreferencesManager.DEFAULT_ADULT_KEYWORDS) {
                    // Evitamos falsos positivos verificando que no sea una palabra común más larga
                    if (containsIsolatedKeyword(lower, keyword)) {
                        blockCurrentPage("Contenido para adultos bloqueado: $keyword")
                        return
                    }
                }
            }
        }
    }

    /**
     * Comprueba si la palabra clave aparece como dominio o palabra aislada
     */
    private fun containsIsolatedKeyword(text: String, keyword: String): Boolean {
        if (!text.contains(keyword)) return false
        // Si aparece como parte de una URL o palabra separada
        val regex = Regex("""(?i)(?:^|[\W_])${Regex.escape(keyword)}(?:[\W_]|$)""")
        return regex.containsMatchIn(text)
    }

    /**
     * Acción de bloqueo: expulsa a la pantalla de inicio y muestra la pantalla informativa
     */
    private fun blockCurrentPage(reason: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_REASON", reason)
        }
        startActivity(intent)
    }

    /**
     * Recorre recursivamente el árbol de vistas para extraer todo el texto visible
     */
    private fun collectAllText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return

        node.text?.let { if (it.isNotBlank()) list.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotBlank()) list.add(it.toString()) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectAllText(child, list)
        }
    }

    override fun onInterrupt() {
        // Callback si el servicio es interrumpido por el sistema
    }
}
