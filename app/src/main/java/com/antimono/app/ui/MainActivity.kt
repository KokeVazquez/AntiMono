package com.antimono.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
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
    private lateinit var blacklistAdapter: BlacklistAdapter
    private var isAuthenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setupPinView()
        setupDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (isAuthenticated) {
            updatePermissionsStatus()
        }
    }

    // --- 1. Autenticación por PIN (6 a 8 dígitos) ---
    private fun setupPinView() {
        val hasPin = prefs.hasPin()

        if (!hasPin) {
            binding.tvPinTitle.text = "Configuración Inicial"
            binding.tvPinSubtitle.text = "Tu esposa debe ingresar un PIN de 6 a 8 dígitos"
            binding.btnSubmitPin.text = "Guardar PIN y Activar"
        } else {
            binding.tvPinTitle.text = "AntiMono"
            binding.tvPinSubtitle.text = "Ingresa el PIN de 6 a 8 dígitos para acceder"
            binding.btnSubmitPin.text = "Desbloquear"
        }

        binding.btnSubmitPin.setOnClickListener {
            val pin = binding.etPin.text?.toString()?.trim() ?: ""
            if (pin.length !in 6..8) {
                Toast.makeText(this, "El PIN debe tener entre 6 y 8 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!prefs.hasPin()) {
                // Registro de PIN inicial por la esposa
                prefs.setPin(pin)
                prefs.isProtectionEnabled = true
                Toast.makeText(this, "PIN configurado y protección armada", Toast.LENGTH_SHORT).show()
                unlockDashboard()
            } else {
                // Verificación
                if (prefs.verifyPin(pin)) {
                    unlockDashboard()
                } else {
                    Toast.makeText(this, "PIN incorrecto", Toast.LENGTH_SHORT).show()
                    binding.etPin.text?.clear()
                }
            }
        }
    }

    private fun unlockDashboard() {
        isAuthenticated = true
        // Sin temporizadores molestos: mientras la app esté abierta tienes acceso total
        binding.layoutPinAuth.visibility = View.GONE
        binding.layoutDashboard.visibility = View.VISIBLE
        binding.etPin.text?.clear()

        updatePermissionsStatus()
        refreshBlacklist()
    }

    private fun lockDashboard() {
        isAuthenticated = false
        prefs.clearTempUnlock()
        binding.layoutDashboard.visibility = View.GONE
        binding.layoutPinAuth.visibility = View.VISIBLE
    }

    // --- 2. Panel de Configuración ---
    private fun setupDashboard() {
        // Botón Bloquear
        binding.btnLockApp.setOnClickListener {
            lockDashboard()
        }

        // Switch Maestro
        binding.switchMasterProtection.isChecked = prefs.isProtectionEnabled
        updateProtectionStatusLabel(prefs.isProtectionEnabled)
        binding.switchMasterProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.isProtectionEnabled = isChecked
            updateProtectionStatusLabel(isChecked)
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

        // Permiso Accesibilidad
        binding.btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Busca 'AntiMono' y activa el servicio", Toast.LENGTH_LONG).show()
        }

        // Permiso Device Admin (Anti-Desinstalación)
        binding.btnEnableDeviceAdmin.setOnClickListener {
            val component = ComponentName(this, AntiUninstallDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_description)
                )
            }
            startActivity(intent)
        }

        // Exclusión de batería (Samsung)
        binding.btnBatteryOptimization.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        // Lista Negra
        blacklistAdapter = BlacklistAdapter { domainToDelete ->
            prefs.removeBlacklistDomain(domainToDelete)
            refreshBlacklist()
            Toast.makeText(this, "Eliminado: $domainToDelete", Toast.LENGTH_SHORT).show()
        }
        binding.rvBlacklist.layoutManager = LinearLayoutManager(this)
        binding.rvBlacklist.adapter = blacklistAdapter

        binding.btnAddDomain.setOnClickListener {
            val domain = binding.etNewDomain.text?.toString()?.trim() ?: ""
            if (domain.isNotEmpty()) {
                prefs.addBlacklistDomain(domain)
                binding.etNewDomain.text?.clear()
                refreshBlacklist()
                Toast.makeText(this, "Sitio agregado a lista negra", Toast.LENGTH_SHORT).show()
            }
        }

        // Cambiar PIN
        binding.btnChangePin.setOnClickListener {
            showChangePinDialog()
        }
    }

    private fun updateProtectionStatusLabel(enabled: Boolean) {
        if (enabled) {
            binding.tvProtectionStatus.text = "🛡️ PROTECCIÓN ACTIVA (Búnker Armado)"
            binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvProtectionStatus.text = "⚪ PROTECCIÓN APAGADA (App Inactiva)"
            binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun updatePermissionsStatus() {
        // Verificar Accesibilidad
        val accessibilityActive = isAccessibilityServiceEnabled()
        if (accessibilityActive) {
            binding.tvAccessibilityStatus.text = "1. Accesibilidad: ACTIVO ✓"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.btnEnableAccessibility.visibility = View.GONE
        } else {
            binding.tvAccessibilityStatus.text = "1. Accesibilidad: INACTIVO ✗"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.btnEnableAccessibility.visibility = View.VISIBLE
        }

        // Verificar Device Admin
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, AntiUninstallDeviceAdminReceiver::class.java)
        val adminActive = dpm.isAdminActive(adminComponent)
        if (adminActive) {
            binding.tvDeviceAdminStatus.text = "2. Anti-Desinstalación: ACTIVO ✓"
            binding.tvDeviceAdminStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.btnEnableDeviceAdmin.visibility = View.GONE
        } else {
            binding.tvDeviceAdminStatus.text = "2. Anti-Desinstalación: INACTIVO ✗"
            binding.tvDeviceAdminStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.btnEnableDeviceAdmin.visibility = View.VISIBLE
        }
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
            Toast.makeText(this, "La optimización de batería ya está desactivada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshBlacklist() {
        val list = prefs.getBlacklist().sorted()
        blacklistAdapter.submitList(list)
    }

    private fun showChangePinDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Nuevo PIN (6 a 8 dígitos)"
        }
        AlertDialog.Builder(this)
            .setTitle("Cambiar PIN de Seguridad")
            .setMessage("Este PIN solo debe conocerlo tu esposa para evitar desactivar la protección por impulso.")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newPin = input.text?.toString()?.trim() ?: ""
                if (newPin.length in 6..8) {
                    prefs.setPin(newPin)
                    Toast.makeText(this, "PIN actualizado con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "El PIN debe tener entre 6 y 8 dígitos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
