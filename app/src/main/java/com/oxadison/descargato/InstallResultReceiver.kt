// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            else -> DownloadEngine.onInstallFailed(describe(status, message))
        }
    }

    private fun describe(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Instalación cancelada."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "La firma del APK no coincide con la app instalada. Hay que firmarlo con la misma clave."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "No hay espacio suficiente para instalar."
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicto con la versión instalada."
        else -> "No se pudo instalar la actualización" + (message?.let { ": $it" } ?: ".")
    }
}
