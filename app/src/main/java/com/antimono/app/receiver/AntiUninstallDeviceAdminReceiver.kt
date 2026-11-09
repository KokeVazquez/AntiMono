package com.antimono.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast
import com.antimono.app.R

class AntiUninstallDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AntiUninstallDeviceAdminReceiver::class.java)

        try {
            // Si tiene privilegios de Device Owner, deshabilitamos Modo Seguro y Depuración
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Toast.makeText(context, "AntiMono: Blindaje de Administrador Activado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val prefs = PreferencesManager(context)
        // Si el interruptor maestro está APAGADO desde la app con el PIN, permite desinstalar/desactivar sin quejas
        if (!prefs.isProtectionEnabled) {
            return null
        }
        return context.getString(R.string.device_admin_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "AntiMono: Blindaje Desactivado", Toast.LENGTH_SHORT).show()
    }
}
