package com.antimono.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.animation.ObjectAnimator
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.antimono.app.R
import com.antimono.app.data.PreferencesManager
import com.antimono.app.databinding.ActivityMainBinding
import com.antimono.app.receiver.AntiUninstallDeviceAdminReceiver
import com.antimono.app.service.AntiMonoAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var domainsAdapter: BlacklistAdapter
    private lateinit var appsAdapter: BlacklistAdapter
    private lateinit var keywordsAdapter: BlacklistAdapter
    private var isAuthenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setupPinView()
        setupDashboard()
        setupCollapsibleSections()
    }

    override fun onResume() {
        super.onResume()
        if (isAuthenticated) {
            updatePermissionsStatus()
        }
    }

    // --- 1. Autenticación por Código de Acceso ---
    private fun setupPinView() {
        val hasPin = prefs.hasPin()

        if (!hasPin) {
            binding.tvPinTitle.text = "Configuración Inicial"
            binding.tvPinSubtitle.text = "Define un código de acceso de 6 a 8 dígitos"
            binding.btnSubmitPin.text = "Guardar y Activar"
        } else {
            binding.tvPinTitle.text = "AntiMono"
            binding.tvPinSubtitle.text = "Introduce el código de acceso"
            binding.btnSubmitPin.text = "Desbloquear"
        }

        binding.btnSubmitPin.setOnClickListener {
            hideKeyboard(binding.etPin)
            val pin = binding.etPin.text?.toString()?.trim() ?: ""
            if (pin.length !in 6..8) {
                Toast.makeText(this, "El código debe tener entre 6 y 8 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!prefs.hasPin()) {
                prefs.setPin(pin)
                prefs.isProtectionEnabled = true
                Toast.makeText(this, "Código guardado y protección activada", Toast.LENGTH_SHORT).show()
                unlockDashboard()
            } else {
                if (prefs.verifyPin(pin)) {
                    unlockDashboard()
                } else {
                    Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
                    binding.etPin.text?.clear()
                }
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun unlockDashboard() {
        isAuthenticated = true
        binding.layoutPinAuth.visibility = View.GONE
        binding.layoutDashboard.visibility = View.VISIBLE
        binding.etPin.text?.clear()

        updatePermissionsStatus()
        refreshDomainsList()
        refreshAppsList()
        refreshKeywordsList()
    }

    private fun lockDashboard() {
        isAuthenticated = false
        binding.layoutDashboard.visibility = View.GONE
        binding.layoutPinAuth.visibility = View.VISIBLE
    }

    // --- 2. Acordeones / Listas Contraíbles ---
    private fun setupCollapsibleSections() {
        // Acordeón Permisos
        binding.headerPermissions.setOnClickListener {
            toggleSection(binding.bodyPermissions, binding.ivChevronPermissions)
        }

        // Acordeón Filtros Automáticos
        binding.headerFilters.setOnClickListener {
            toggleSection(binding.bodyFilters, binding.ivChevronFilters)
        }

        // Sub-Acordeón Palabras Prohibidas
        binding.headerKeywords.setOnClickListener {
            toggleSection(binding.bodyKeywords, binding.ivChevronKeywords)
        }

        // Acordeón Apps
        binding.headerApps.setOnClickListener {
            toggleSection(binding.bodyApps, binding.ivChevronApps)
        }

        // Acordeón Sitios Web
        binding.headerDomains.setOnClickListener {
            toggleSection(binding.bodyDomains, binding.ivChevronDomains)
        }
    }

    private fun toggleSection(body: View, chevron: ImageView) {
        val willExpand = body.visibility != View.VISIBLE
        body.visibility = if (willExpand) View.VISIBLE else View.GONE
        val targetRotation = if (willExpand) 180f else 0f
        ObjectAnimator.ofFloat(chevron, "rotation", chevron.rotation, targetRotation).setDuration(250).start()
    }

    // --- 3. Panel de Control y Ajustes ---
    private fun setupDashboard() {
        binding.btnLockApp.setOnClickListener {
            lockDashboard()
        }

        // SWITCH GIGANTE EN EL CENTRO
        binding.switchMasterProtection.isChecked = prefs.isProtectionEnabled
        updateProtectionStatusLabel(prefs.isProtectionEnabled)
        binding.switchMasterProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.isProtectionEnabled = isChecked
            updateProtectionStatusLabel(isChecked)
        }

        // Switches de Permisos del Sistema
        binding.switchAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Selecciona 'AntiMono' y activa el servicio", Toast.LENGTH_LONG).show()
        }

        binding.switchDeviceAdmin.setOnClickListener {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(this, AntiUninstallDeviceAdminReceiver::class.java)
            if (!dpm.isAdminActive(component)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.device_admin_description)
                    )
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "El Administrador de Dispositivo ya está activo", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        // Switches de Filtros
        binding.switchAdultFilter.isChecked = prefs.isAdultBlockEnabled
        binding.switchAdultFilter.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAdultBlockEnabled = isChecked
        }

        binding.switchIncognitoFilter.isChecked = prefs.isIncognitoBlockEnabled
        binding.switchIncognitoFilter.setOnCheckedChangeListener { _, isChecked ->
            prefs.isIncognitoBlockEnabled = isChecked
        }

        binding.switchKeywordGuardian.isChecked = prefs.isKeywordGuardianEnabled
        binding.switchKeywordGuardian.setOnCheckedChangeListener { _, isChecked ->
            prefs.isKeywordGuardianEnabled = isChecked
        }

        binding.btnConfigureMonitoredApps.setOnClickListener {
            showChooseMonitoredAppsDialog()
        }

        // 1. Palabras Prohibidas (Keywords)
        keywordsAdapter = BlacklistAdapter { word ->
            prefs.removeProhibitedKeyword(word)
            refreshKeywordsList()
            Toast.makeText(this, "Eliminada: $word", Toast.LENGTH_SHORT).show()
        }
        binding.rvKeywords.layoutManager = LinearLayoutManager(this)
        binding.rvKeywords.adapter = keywordsAdapter

        binding.btnAddKeyword.setOnClickListener {
            hideKeyboard(binding.etNewKeyword)
            val word = binding.etNewKeyword.text?.toString()?.trim() ?: ""
            if (word.isNotEmpty()) {
                prefs.addProhibitedKeyword(word)
                binding.etNewKeyword.text?.clear()
                refreshKeywordsList()
                Toast.makeText(this, "Palabra añadida al filtro", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Apps Bloqueadas
        binding.switchAppBlocking.isChecked = prefs.isAppBlockEnabled
        binding.switchAppBlocking.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAppBlockEnabled = isChecked
        }

        binding.btnSelectAppsToBlock.setOnClickListener {
            showChooseBlockedAppsDialog()
        }

        appsAdapter = BlacklistAdapter { app ->
            prefs.removeBlockedApp(app)
            refreshAppsList()
            Toast.makeText(this, "Desbloqueada: $app", Toast.LENGTH_SHORT).show()
        }
        binding.rvBlockedApps.layoutManager = LinearLayoutManager(this)
        binding.rvBlockedApps.adapter = appsAdapter

        binding.btnAddApp.setOnClickListener {
            hideKeyboard(binding.etNewApp)
            val app = binding.etNewApp.text?.toString()?.trim() ?: ""
            if (app.isNotEmpty()) {
                prefs.addBlockedApp(app)
                binding.etNewApp.text?.clear()
                refreshAppsList()
                Toast.makeText(this, "Aplicación añadida", Toast.LENGTH_SHORT).show()
            }
        }

        binding.chipAddTwitter.setOnClickListener {
            prefs.addBlockedApp("com.twitter.android")
            prefs.addBlockedApp("twitter")
            refreshAppsList()
            Toast.makeText(this, "X añadida", Toast.LENGTH_SHORT).show()
        }
        binding.chipAddTikTok.setOnClickListener {
            prefs.addBlockedApp("com.zhiliaoapp.musically")
            prefs.addBlockedApp("tiktok")
            refreshAppsList()
            Toast.makeText(this, "TikTok añadida", Toast.LENGTH_SHORT).show()
        }
        binding.chipAddInstagram.setOnClickListener {
            prefs.addBlockedApp("com.instagram.android")
            prefs.addBlockedApp("instagram")
            refreshAppsList()
            Toast.makeText(this, "Instagram añadida", Toast.LENGTH_SHORT).show()
        }
        binding.chipAddReddit.setOnClickListener {
            prefs.addBlockedApp("com.reddit.frontpage")
            prefs.addBlockedApp("reddit")
            refreshAppsList()
            Toast.makeText(this, "Reddit añadida", Toast.LENGTH_SHORT).show()
        }
        binding.chipAddTelegram.setOnClickListener {
            prefs.addBlockedApp("org.telegram.messenger")
            prefs.addBlockedApp("telegram")
            refreshAppsList()
            Toast.makeText(this, "Telegram añadida", Toast.LENGTH_SHORT).show()
        }
        binding.chipAddViber.setOnClickListener {
            prefs.addBlockedApp("com.viber.voip")
            prefs.addBlockedApp("viber")
            refreshAppsList()
            Toast.makeText(this, "Viber añadida", Toast.LENGTH_SHORT).show()
        }
        binding.chipAddLine.setOnClickListener {
            prefs.addBlockedApp("jp.naver.line.android")
            prefs.addBlockedApp("line")
            refreshAppsList()
            Toast.makeText(this, "LINE añadida", Toast.LENGTH_SHORT).show()
        }

        // 3. Sitios Web Bloqueados
        domainsAdapter = BlacklistAdapter { domain ->
            prefs.removeBlacklistDomain(domain)
            refreshDomainsList()
            Toast.makeText(this, "Eliminado: $domain", Toast.LENGTH_SHORT).show()
        }
        binding.rvBlacklist.layoutManager = LinearLayoutManager(this)
        binding.rvBlacklist.adapter = domainsAdapter

        binding.btnAddDomain.setOnClickListener {
            hideKeyboard(binding.etNewDomain)
            val domain = binding.etNewDomain.text?.toString()?.trim() ?: ""
            if (domain.isNotEmpty()) {
                prefs.addBlacklistDomain(domain)
                binding.etNewDomain.text?.clear()
                refreshDomainsList()
                Toast.makeText(this, "Dominio añadido", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchWebBlocking.isChecked = prefs.isWebBlockEnabled
        binding.switchWebBlocking.setOnCheckedChangeListener { _, isChecked ->
            prefs.isWebBlockEnabled = isChecked
        }

        // Cambiar PIN
        binding.btnChangePin.setOnClickListener {
            showChangePinDialog()
        }
    }

    private fun updateProtectionStatusLabel(enabled: Boolean) {
        if (enabled) {
            binding.tvProtectionStatus.text = "Protección Armada"
            binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.ios_green))
            binding.tvProtectionSubtitle.text = "El dispositivo se encuentra totalmente protegido"
        } else {
            binding.tvProtectionStatus.text = "Protección Desactivada"
            binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.ios_red))
            binding.tvProtectionSubtitle.text = "Los filtros y restricciones están en pausa"
        }
    }

    private fun updatePermissionsStatus() {
        val accessibilityActive = isAccessibilityServiceEnabled()
        binding.switchAccessibility.isChecked = accessibilityActive

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, AntiUninstallDeviceAdminReceiver::class.java)
        val adminActive = dpm.isAdminActive(adminComponent)
        binding.switchDeviceAdmin.isChecked = adminActive

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryActive = powerManager.isIgnoringBatteryOptimizations(packageName)
        binding.switchBattery.isChecked = batteryActive

        var activeCount = 0
        if (accessibilityActive) activeCount++
        if (adminActive) activeCount++
        if (batteryActive) activeCount++

        binding.tvPermissionsSummary.text = "Permisos activos: $activeCount de 3"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName &&
                service.resolveInfo.serviceInfo.name == AntiMonoAccessibilityService::class.java.name
            ) {
                return true
            }
        }
        return false
    }

    private fun requestIgnoreBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Exclusión de batería ya configurada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshKeywordsList() {
        val list = prefs.getProhibitedKeywords().sorted()
        keywordsAdapter.submitList(list)
    }

    private fun refreshDomainsList() {
        val list = prefs.getBlacklist().sorted()
        domainsAdapter.submitList(list)
    }

    private fun refreshAppsList() {
        val list = prefs.getBlockedApps().sorted()
        appsAdapter.submitList(list)
    }

    private data class InstalledAppItem(val name: String, val packageName: String)

    private fun showChooseMonitoredAppsDialog() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        val apps = resolveInfos
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val label = it.loadLabel(pm).toString()
                val pkg = it.activityInfo.packageName
                InstalledAppItem(label, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(this, "No se encontraron aplicaciones", Toast.LENGTH_SHORT).show()
            return
        }

        val currentMonitored = prefs.getMonitoredKeyboardApps()
        val appNames = apps.map { it.name }.toTypedArray()
        val checkedItems = apps.map { item ->
            currentMonitored.contains(item.packageName)
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Vigilar Teclado en Aplicaciones")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Guardar") { _, _ ->
                val newSelected = mutableSetOf<String>()
                for (i in apps.indices) {
                    if (checkedItems[i]) {
                        newSelected.add(apps[i].packageName)
                    }
                }
                prefs.setMonitoredKeyboardApps(newSelected)
                Toast.makeText(this, "Configuración guardada (${newSelected.size} apps)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showChooseBlockedAppsDialog() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        val apps = resolveInfos
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val label = it.loadLabel(pm).toString()
                val pkg = it.activityInfo.packageName
                InstalledAppItem(label, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(this, "No se encontraron aplicaciones", Toast.LENGTH_SHORT).show()
            return
        }

        val currentBlocked = prefs.getBlockedApps()
        val appNames = apps.map { it.name }.toTypedArray()
        val checkedItems = apps.map { item ->
            currentBlocked.contains(item.packageName) || currentBlocked.contains(item.name.lowercase())
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Bloquear Aplicaciones Instaladas")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Guardar") { _, _ ->
                val newBlocked = currentBlocked.toMutableSet()
                for (i in apps.indices) {
                    val pkg = apps[i].packageName
                    val nameLower = apps[i].name.lowercase()
                    if (checkedItems[i]) {
                        newBlocked.add(pkg)
                    } else {
                        newBlocked.remove(pkg)
                        newBlocked.remove(nameLower)
                    }
                }
                prefs.setBlockedApps(newBlocked)
                refreshAppsList()
                Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showChangePinDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Código de acceso (6 a 8 dígitos)"
        }
        AlertDialog.Builder(this)
            .setTitle("Cambiar Código de Acceso")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newPin = input.text?.toString()?.trim() ?: ""
                if (newPin.length in 6..8) {
                    prefs.setPin(newPin)
                    Toast.makeText(this, "Código actualizado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "El código debe tener entre 6 y 8 dígitos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
