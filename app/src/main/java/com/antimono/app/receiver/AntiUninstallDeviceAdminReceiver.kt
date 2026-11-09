package com.antimono.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.antimono.app.R

class AntiUninstallDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "AntiMono: Protección de Administrador Activada", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "AntiMono: Protección de Administrador Desactivada", Toast.LENGTH_SHORT).show()
    }
}
