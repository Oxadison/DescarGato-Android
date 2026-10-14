// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

enum class Quality(val label: String) {
    BEST("Mejor Calidad"),
    MULTI("Multi-Lenguaje"),
    P1080("1080p"),
    P720("720p"),
    P480("480p"),
    P360("360p"),
    AUDIO("Solo Audio");

    val height: Int?
        get() = when (this) {
            P1080 -> 1080
            P720 -> 720
            P480 -> 480
            P360 -> 360
            else -> null
        }
}
