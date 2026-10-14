// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppUpdate {
    const val REPO = "Oxadison/DescarGato-Android"

    private const val PREFS = "engine"
    private const val KEY_API_OVERRIDE = "update_api_url"

    data class Info(val version: String, val pageUrl: String, val apkUrl: String?, val sizeBytes: Long)

    data class Result(val info: Info?, val error: String?)

    private fun apiUrl(context: Context): String {
        if (BuildConfig.DEBUG) {
            val o = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_OVERRIDE, null)
            if (!o.isNullOrBlank()) return o
        }
        return "https://api.github.com/repos/$REPO/releases/latest"
    }

    fun check(context: Context, currentVersion: String): Result {
        val conn = URL(apiUrl(context)).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "DescarGato-Android")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code == 404) return Result(null, null)
            if (code != 200) return Result(null, "GitHub respondió HTTP $code")

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").trim().trimStart('v', 'V')
            if (tag.isEmpty() || !isNewer(tag, currentVersion)) return Result(null, null)

            val (apkUrl, size) = pickApk(json)
            Result(Info(tag, json.optString("html_url"), apkUrl, size), null)
        } catch (e: Exception) {
            Result(null, e.message ?: e.toString())
        } finally {
            conn.disconnect()
        }
    }

    private fun pickApk(release: JSONObject): Pair<String?, Long> {
        val assets = release.optJSONArray("assets") ?: return null to 0L
        val apks = ArrayList<Triple<String, String, Long>>()
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apks.add(Triple(name.lowercase(), a.optString("browser_download_url"), a.optLong("size")))
            }
        }
        if (apks.isEmpty()) return null to 0L
        for (abi in Build.SUPPORTED_ABIS) {
            apks.firstOrNull { abi.lowercase() in it.first }?.let { return it.second to it.third }
        }
        apks.firstOrNull { "universal" in it.first }?.let { return it.second to it.third }
        return apks[0].second to apks[0].third
    }

    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
