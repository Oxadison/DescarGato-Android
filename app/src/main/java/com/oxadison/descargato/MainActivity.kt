// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractUrl(intent)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DescarGatoScreen(
                        sharedUrl = sharedUrl,
                        onSharedConsumed = { sharedUrl = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractUrl(intent)?.let { sharedUrl = it }
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("""https?://\S+""", RegexOption.IGNORE_CASE).find(text)?.value
    }
}

private fun isValidUrl(url: String): Boolean {
    if (!(url.startsWith("http://", true) || url.startsWith("https://", true))) return false
    return !Uri.parse(url).host.isNullOrBlank()
}

@Composable
private fun DescarGatoScreen(sharedUrl: String?, onSharedConsumed: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val state by DownloadEngine.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var url by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf(Quality.BEST) }
    var menuOpen by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showAudioChoice by remember { mutableStateOf(false) }

    var checking by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<AppUpdate.Info?>(null) }
    var needInstallPermission by remember { mutableStateOf<AppUpdate.Info?>(null) }
    var engineDialog by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            url = sharedUrl
            formError = null
            onSharedConsumed()
        }
    }

    fun startNow(mp3: Boolean) = DownloadEngine.start(app, url.trim(), quality, mp3)

    fun onDownloadClick() {
        val u = url.trim()
        if (!isValidUrl(u)) {
            formError = "Por favor, ingresa una URL válida."
            return
        }
        formError = null
        when (quality) {
            Quality.AUDIO -> showAudioChoice = true
            else -> startNow(false)
        }
    }

    fun startAppUpdate(info: AppUpdate.Info) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            needInstallPermission = info
            return
        }
        DownloadEngine.installAppUpdate(app, info)
    }

    fun onUpdaterClick() {
        if (checking) return
        checking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { AppUpdate.check(app, BuildConfig.VERSION_NAME) }
            checking = false
            when {
                result.info != null -> {
                    DownloadEngine.setAppUpdate(result.info)
                    updateDialog = result.info
                }
                result.error != null ->
                    DownloadEngine.logMessage("No se pudo buscar actualizaciones: ${result.error}")
                else -> engineDialog = true
            }
        }
    }

    val working = state.phase == Phase.DOWNLOADING || state.phase == Phase.PREPARING ||
        state.phase == Phase.SAVING || state.updatingApp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "DescarGato",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "♥ De Oxadison para Suidelame ♥",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        state.appUpdate?.let { info ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Versión v${info.version} disponible",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = !state.busy,
                        onClick = { updateDialog = info }
                    ) { Text("Actualizar") }
                }
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; formError = null },
            label = { Text("URL del Video") },
            singleLine = true,
            isError = formError != null,
            supportingText = formError?.let { { Text(it) } },
            enabled = !state.busy,
            trailingIcon = {
                TextButton(
                    enabled = !state.busy,
                    onClick = { clipboard.getText()?.text?.let { url = it.trim(); formError = null } }
                ) { Text("Pegar") }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Opciones de descarga: ${quality.label}",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Quality.entries.forEach { q ->
                    DropdownMenuItem(
                        text = { Text(q.label) },
                        onClick = { quality = q; menuOpen = false }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onDownloadClick() },
                enabled = state.engineReady && !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("Descargar") }
            OutlinedButton(
                onClick = { DownloadEngine.cancel() },
                enabled = state.busy && !state.updating && !state.updatingApp,
                modifier = Modifier.weight(1f)
            ) { Text("Cancelar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { DownloadEngine.clear(); url = ""; formError = null },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("Limpiar") }
            OutlinedButton(
                onClick = { onUpdaterClick() },
                enabled = state.engineReady && !state.busy && !checking,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (checking) "Buscando…" else "Actualizador",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (working) {
            if ((state.phase == Phase.DOWNLOADING || state.updatingApp) && state.progress > 0f) {
                LinearProgressIndicator(
                    progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        val statusColor = when (state.phase) {
            Phase.DONE -> Color(0xFF2E7D32)
            Phase.ERROR -> Color(0xFFC62828)
            Phase.CANCELLED -> Color(0xFFD2691E)
            else -> MaterialTheme.colorScheme.onSurface
        }
        val statusText = state.initError?.let { "Error: $it" } ?: state.status
        Text(
            statusText,
            color = statusColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        val listState = rememberLazyListState()
        LaunchedEffect(state.logs.size) {
            if (state.logs.isNotEmpty()) listState.scrollToItem(state.logs.lastIndex)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            SelectionContainer {
                LazyColumn(state = listState) {
                    itemsIndexed(state.logs) { _, line ->
                        Text(
                            line,
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        Text(
            "© 2026 Creado por Oxadison",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    updateDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { updateDialog = null },
            title = { Text("Actualizador") },
            text = {
                Text(
                    "¡Se ha encontrado la versión v${info.version} de DescarGato!\n\n" +
                        "¿Deseas descargar e instalar la actualización ahora?\n\n" +
                        "Android te pedirá confirmar la instalación y la app se reiniciará."
                )
            },
            confirmButton = {
                TextButton(onClick = { updateDialog = null; startAppUpdate(info) }) { Text("Actualizar") }
            },
            dismissButton = {
                TextButton(onClick = { updateDialog = null }) { Text("Ahora no") }
            }
        )
    }

    needInstallPermission?.let { info ->
        AlertDialog(
            onDismissRequest = { needInstallPermission = null },
            title = { Text("Permiso necesario") },
            text = {
                Text(
                    "Para actualizarse, DescarGato necesita permiso para instalar aplicaciones.\n\n" +
                        "Se abrirá la configuración de Android: activa \"Permitir de esta fuente\" " +
                        "y vuelve a pulsar Actualizador."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    needInstallPermission = null
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("Abrir configuración") }
            },
            dismissButton = {
                TextButton(onClick = { needInstallPermission = null }) { Text("Cancelar") }
            }
        )
    }

    if (engineDialog) {
        AlertDialog(
            onDismissRequest = { engineDialog = false },
            title = { Text("Actualizador") },
            text = {
                val current = if (DownloadEngine.ytdlpChannel(app) == DownloadEngine.CHANNEL_NIGHTLY)
                    "Nightly" else "Estable"
                Text(
                    "DescarGato ya se encuentra en su última versión.\n\n" +
                        "Sin embargo, puedes renovar el motor de descarga (yt-dlp).\n\n" +
                        "• Estable: versiones probadas (recomendado).\n" +
                        "• Nightly: las últimas correcciones, a veces antes de que salgan en la estable.\n\n" +
                        "Canal actual: $current"
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        engineDialog = false
                        DownloadEngine.updateEngine(app, DownloadEngine.CHANNEL_STABLE)
                    }) { Text("Estable") }
                    TextButton(onClick = {
                        engineDialog = false
                        DownloadEngine.updateEngine(app, DownloadEngine.CHANNEL_NIGHTLY)
                    }) { Text("Nightly") }
                }
            },
            dismissButton = {
                TextButton(onClick = { engineDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAudioChoice) {
        AlertDialog(
            onDismissRequest = { showAudioChoice = false },
            title = { Text("Solo Audio") },
            text = {
                Text(
                    "¿Cómo deseas guardar el archivo de audio?\n\n" +
                        "• Original: mejor calidad de sonido, descarga rápida.\n\n" +
                        "• MP3: mayor compatibilidad, conversión lenta."
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showAudioChoice = false; startNow(false) }) { Text("Original") }
                    TextButton(onClick = { showAudioChoice = false; startNow(true) }) { Text("MP3") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAudioChoice = false }) { Text("Cancelar") }
            }
        )
    }
}
