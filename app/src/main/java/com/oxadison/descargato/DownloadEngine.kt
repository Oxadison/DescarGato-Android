// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class Phase { IDLE, PREPARING, DOWNLOADING, SAVING, DONE, ERROR, CANCELLED }

data class UiState(
    val engineReady: Boolean = false,
    val initError: String? = null,
    val phase: Phase = Phase.IDLE,
    val progress: Float = 0f,
    val etaSeconds: Long = 0,
    val status: String = "",
    val logs: List<String> = emptyList(),
    val updating: Boolean = false,
    val updatingApp: Boolean = false,
    val appUpdate: AppUpdate.Info? = null,
) {
    val busy: Boolean
        get() = updating || updatingApp || phase == Phase.PREPARING || phase == Phase.DOWNLOADING || phase == Phase.SAVING
}

object DownloadEngine {
    private const val PROCESS_ID = "descargato-main"
    private const val MAX_LOG_LINES = 400
    private const val IDLE_TEXT = "Realiza alguna acción para comenzar"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UiState(status = "Verificando entorno…"))
    val state: StateFlow<UiState> = _state.asStateFlow()

    @Volatile
    private var initStarted = false

    @Volatile
    private var cancelRequested = false

    private class UserCancelled : Exception()

    fun initialize(app: Application) {
        if (initStarted) return
        initStarted = true
        scope.launch {
            File(app.cacheDir, "gatodownload").deleteRecursively()
            File(app.cacheDir, "updates").deleteRecursively()
            try {
                log("Verificando entorno…")
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                _state.update { it.copy(engineReady = true, status = IDLE_TEXT) }
                log("Entorno verificado correctamente.")
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                _state.update { it.copy(initError = msg, status = "Error durante la preparación") }
                log("[Init] $msg")
                return@launch
            }
            autoUpdateYtDlpIfStale(app)
            val info = AppUpdate.check(app, BuildConfig.VERSION_NAME).info
            if (info != null) {
                _state.update { it.copy(appUpdate = info) }
                log("¡Versión v${info.version} disponible!")
            }
        }
    }

    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_NIGHTLY = "nightly"
    private const val PREF_CHANNEL = "ytdlp_channel"

    fun ytdlpChannel(app: Application): String =
        app.getSharedPreferences("engine", Application.MODE_PRIVATE)
            .getString(PREF_CHANNEL, CHANNEL_STABLE) ?: CHANNEL_STABLE

    private fun channelFor(app: Application): YoutubeDL.UpdateChannel =
        if (ytdlpChannel(app) == CHANNEL_NIGHTLY) YoutubeDL.UpdateChannel.NIGHTLY
        else YoutubeDL.UpdateChannel.STABLE

    private fun autoUpdateYtDlpIfStale(app: Application) {
        val prefs = app.getSharedPreferences("engine", Application.MODE_PRIVATE)
        val last = prefs.getLong("ytdlp_last_update", 0L)
        if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) return
        _state.update { it.copy(updating = true, status = "Actualizando motor…") }
        try {
            log("Buscando actualizaciones de yt-dlp (${ytdlpChannel(app)})…")
            val result = YoutubeDL.getInstance().updateYoutubeDL(app, channelFor(app))
            val version = YoutubeDL.getInstance().versionName(app)
            log(
                if (result == YoutubeDL.UpdateStatus.DONE) "yt-dlp actualizado a $version."
                else "yt-dlp al día ($version)."
            )
            prefs.edit().putLong("ytdlp_last_update", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            log("[Update] No se pudo actualizar yt-dlp: ${e.message ?: e}")
        } finally {
            _state.update { it.copy(updating = false, status = IDLE_TEXT) }
        }
    }

    fun start(app: Application, url: String, quality: Quality, mp3: Boolean) {
        val s = _state.value
        if (s.busy || !s.engineReady) return
        cancelRequested = false
        _state.update { it.copy(phase = Phase.PREPARING, progress = 0f, etaSeconds = 0, status = "Descargando…") }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        scope.launch { runDownload(app, url, quality, mp3) }
    }

    private fun runDownload(app: Application, url: String, quality: Quality, mp3: Boolean) {
        val staging = File(app.cacheDir, "gatodownload/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            setPhase(Phase.DOWNLOADING, "Descargando…")
            if (quality.height != null) log("Buscando la calidad ${quality.label} o la más cercana inferior…")

            val request = CommandBuilder.build(app, url, quality, mp3, staging)
            YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, eta, line ->
                onProgress(progress, eta, line)
            }
            if (cancelRequested) throw UserCancelled()

            setPhase(Phase.SAVING, "Guardando en Descargas…")
            val files = staging.listFiles()?.filter { Saver.isFinalFile(it) }.orEmpty()
            if (files.isEmpty()) throw IllegalStateException("No se generó ningún archivo de salida.")
            files.forEach { f ->
                val name = Saver.saveToDownloads(app, f)
                log("Guardado en Descargas/DescarGato: $name")
            }
            _state.update { it.copy(phase = Phase.DONE, progress = 100f, status = "Completado") }
        } catch (e: UserCancelled) {
            markCancelled()
        } catch (e: Exception) {
            if (cancelRequested) {
                markCancelled()
            } else {
                val msg = friendlyError(e.message ?: e.toString())
                log("[Error] $msg")
                _state.update { it.copy(phase = Phase.ERROR, status = "Error") }
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun onProgress(progress: Float, eta: Long, line: String) {
        if (progress >= 0f) _state.update { it.copy(progress = progress, etaSeconds = eta) }
        val isProgressLine = line.startsWith("[download]") && '%' in line
        val isNoise = "already is in target format" in line || "Not converting media file" in line
        if (!isProgressLine && !isNoise && line.isNotBlank()) {
            if (line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") ||
                line.startsWith("[VideoConvertor]") || line.startsWith("[EmbedSubtitle]")
            ) {
                _state.update { it.copy(status = "Procesando…") }
            }
            log(cleanPaths(line.trim()))
        }
    }

    private val internalPath = Regex("""/data/[^\s"]*/gatodownload/[0-9a-fA-F-]+/""")
    private fun cleanPaths(line: String) = internalPath.replace(line, "")

    private fun friendlyError(raw: String): String = when {
        "Requested format is not available" in raw ->
            "Calidad no disponible para este video. Prueba con otra opción."
        "Sign in to confirm" in raw || "not a bot" in raw ->
            "El sitio pide verificación. Prueba «Actualizar motor» e inténtalo de nuevo."
        else -> raw.lines().lastOrNull { it.isNotBlank() }?.take(300) ?: raw.take(300)
    }

    fun cancel() {
        if (!_state.value.busy || _state.value.updating || _state.value.updatingApp) return
        cancelRequested = true
        YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
        log("Cancelando…")
    }

    private fun markCancelled() {
        log("Proceso cancelado por el usuario.")
        _state.update { it.copy(phase = Phase.CANCELLED, status = "Cancelado") }
    }

    fun updateEngine(app: Application, channel: String = ytdlpChannel(app)) {
        val s = _state.value
        if (s.busy || !s.engineReady) return
        app.getSharedPreferences("engine", Application.MODE_PRIVATE).edit()
            .putString(PREF_CHANNEL, channel).apply()
        _state.update { it.copy(updating = true, status = "Actualizando…") }
        scope.launch {
            try {
                log("Buscando actualizaciones de yt-dlp ($channel)…")
                val result = YoutubeDL.getInstance().updateYoutubeDL(app, channelFor(app))
                val version = YoutubeDL.getInstance().versionName(app)
                when (result) {
                    YoutubeDL.UpdateStatus.DONE -> log("yt-dlp actualizado a $version.")
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> log("yt-dlp ya está actualizado ($version).")
                    else -> log("No se encontraron novedades.")
                }
                app.getSharedPreferences("engine", Application.MODE_PRIVATE).edit()
                    .putLong("ytdlp_last_update", System.currentTimeMillis()).apply()
                _state.update { it.copy(updating = false, phase = Phase.IDLE, status = IDLE_TEXT) }
            } catch (e: Exception) {
                log("[Update][Error] ${e.message ?: e}")
                _state.update {
                    it.copy(updating = false, phase = Phase.ERROR, status = "Error durante la actualización")
                }
            }
        }
    }

    fun setAppUpdate(info: AppUpdate.Info?) = _state.update { it.copy(appUpdate = info) }

    fun installAppUpdate(app: Application, info: AppUpdate.Info) {
        if (_state.value.busy) return
        val url = info.apkUrl
        if (url == null) {
            log("La versión v${info.version} no incluye un APK descargable. Revisa la página de la release.")
            _state.update { it.copy(phase = Phase.ERROR, status = "Sin APK en la release") }
            return
        }
        _state.update {
            it.copy(updatingApp = true, phase = Phase.IDLE, progress = 0f, status = "Descargando actualización…")
        }
        scope.launch {
            try {
                log("Descargando la versión v${info.version}…")
                val apk = Updater.download(app, url, info.version) { pct ->
                    _state.update { it.copy(progress = pct, status = "Descargando actualización… ${pct.toInt()}%") }
                }
                log("Verificando el paquete descargado…")
                Updater.verify(app, apk)?.let { throw IllegalStateException(it) }
                log("Instalando… confirma en la pantalla del sistema.")
                Updater.install(app, apk)
                apk.delete()
                _state.update { it.copy(updatingApp = false, progress = 100f, status = "Confirma la instalación") }
            } catch (e: Exception) {
                onInstallFailed(e.message ?: e.toString())
            }
        }
    }

    fun onInstallFailed(message: String) {
        log("[Update][Error] $message")
        _state.update {
            it.copy(updatingApp = false, phase = Phase.ERROR, progress = 0f, status = "Error durante la actualización")
        }
    }

    fun logMessage(line: String) = log(line)

    fun clear() {
        if (_state.value.busy) return
        _state.update { it.copy(logs = emptyList(), phase = Phase.IDLE, progress = 0f, status = IDLE_TEXT) }
    }

    private fun setPhase(phase: Phase, status: String) =
        _state.update { it.copy(phase = phase, status = status) }

    private fun log(line: String) =
        _state.update { it.copy(logs = (it.logs + line).takeLast(MAX_LOG_LINES)) }
}
