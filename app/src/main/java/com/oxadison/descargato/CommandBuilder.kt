// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

object CommandBuilder {
    private const val OUTPUT_TEMPLATE = "DescarGato - %(title).50s - %(extractor_key)s.%(ext)s"

    private const val AVC = "[vcodec^=avc]"
    private const val AAC = "[ext=m4a]"

    fun build(context: Context, url: String, quality: Quality, mp3: Boolean, outDir: File): YoutubeDLRequest {
        val req = YoutubeDLRequest(url)
        val qjs = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        if (qjs.exists()) req.addOption("--js-runtimes", "quickjs:${qjs.absolutePath}")
        req.addOption("--extractor-args", "youtube:player_client=all")
        req.addOption("-o", File(outDir, OUTPUT_TEMPLATE).absolutePath)
        req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        req.addOption("-N", 4)

        val height = quality.height
        when {
            quality == Quality.AUDIO -> {
                req.addOption("-f", "bestaudio$AAC/bestaudio/best")
                req.addOption("-x")
                if (mp3) {
                    req.addOption("--audio-format", "mp3")
                    req.addOption("--audio-quality", "320K")
                }
            }

            quality == Quality.MULTI -> {
                req.addOption(
                    "-f",
                    "bestvideo$AVC+bestaudio$AAC[language^=es]+bestaudio$AAC/" +
                        "bestvideo$AVC+bestaudio$AAC/" +
                        "bestvideo+bestaudio[language^=es]+bestaudio/bestvideo+bestaudio/best"
                )
                req.addOption("--audio-multistreams")
                req.addOption("--write-subs")
                req.addOption("--embed-subs")
                req.addOption("--embed-metadata")
                req.addOption("--convert-subs", "srt")
                req.addOption("--sub-langs", "es.*,en.*")
                applyUniversalContainer(req)
            }

            height != null -> {
                req.addOption(
                    "-f",
                    "bestvideo[height<=$height]$AVC+bestaudio$AAC/" +
                        "best[height<=$height]$AVC/" +
                        "bestvideo[height<=$height]+bestaudio/best[height<=$height]"
                )
                applyUniversalContainer(req)
            }

            else -> {
                req.addOption(
                    "-f",
                    "bestvideo$AVC+bestaudio$AAC/best$AVC/bestvideo+bestaudio/best"
                )
                applyUniversalContainer(req)
            }
        }
        return req
    }

    private fun applyUniversalContainer(req: YoutubeDLRequest) {
        req.addOption("--merge-output-format", "mp4/mkv")
        req.addOption("--recode-video", "mp4")
        req.addOption(
            "--postprocessor-args",
            "VideoConvertor:-c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p -c:a aac -movflags +faststart"
        )
    }
}
