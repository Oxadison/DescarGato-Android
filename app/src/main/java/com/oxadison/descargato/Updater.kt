// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val ACTION_RESULT = "com.oxadison.descargato.INSTALL_RESULT"

    private const val MAX_ATTEMPTS = 4

    fun download(context: Context, url: String, version: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { if (it.name != "DescarGato-$version.apk.part") it.delete() }
        }
        val part = File(dir, "DescarGato-$version.apk.part")
        val out = File(dir, "DescarGato-$version.apk")
        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadInto(url, part, onProgress)
                break
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS) throw e
                Thread.sleep(1500L * attempt)
            }
        }
        if (out.exists()) out.delete()
        if (!part.renameTo(out)) throw IOException("No se pudo guardar el APK descargado.")
        return out
    }

    private fun downloadInto(url: String, dest: File, onProgress: (Float) -> Unit) {
        val offset = if (dest.exists()) dest.length() else 0L
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "DescarGato-Android")
            if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")

            val code = conn.responseCode
            val resumed = code == 206
            if (code != 200 && !resumed) throw IOException("La descarga respondió HTTP $code")
            val start = if (resumed) offset else 0L
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong + start else -1L

            var done = start
            var last = -1
            java.io.FileOutputStream(dest, resumed).use { o ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress(pct.toFloat()) }
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("Descarga incompleta ($done de $total bytes)")
        } finally {
            conn.disconnect()
        }
    }

    fun verify(context: Context, apk: File): String? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return "El archivo descargado no es un APK válido (¿descarga incompleta?)."
        if (archive.packageName != context.packageName) return "El APK descargado pertenece a otra aplicación."
        val current = pm.getPackageInfo(context.packageName, 0).longVersionCode
        if (archive.longVersionCode <= current) return "El APK descargado no es más nuevo que la versión instalada."
        return null
    }

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            FileInputStream(apk).use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }
}
