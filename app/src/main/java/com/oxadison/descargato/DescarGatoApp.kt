// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.app.Application

class DescarGatoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadEngine.initialize(this)
    }
}
