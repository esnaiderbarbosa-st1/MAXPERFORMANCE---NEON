package com.example.utils

import com.example.BuildConfig

/**
 * Informações centrais de compilação, versão e data/hora do MAX PERFORMANCE.
 */
object AppBuildInfo {
    const val APP_NAME = "MAX PERFORMANCE"
    val VERSION_NAME: String = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("2.1.0-RC1 (v5.9.7)")
    val BUILD_NUMBER: Int = runCatching { BuildConfig.BUILD_NUMBER }.getOrDefault(265)

    val BUILD_TIME: String = try {
        BuildConfig.BUILD_TIME
    } catch (e: Throwable) {
        "17/10/2026 13:35"
    }

    /**
     * Assinatura completa: "MAX PERFORMANCE v2.5 · Build #25 · Atualizado em 17/10/2026 13:35"
     */
    val fullSignature: String
        get() = "$APP_NAME v$VERSION_NAME · Build #$BUILD_NUMBER · Atualizado em $BUILD_TIME"

    /**
     * Assinatura compacta para menu / modais: "v2.5 (Build #25) • 17/10 13:35"
     */
    val compactSignature: String
        get() {
            val shortTime = if (BUILD_TIME.length >= 16) {
                // "17/10/2026 13:35" -> "17/10 13:35"
                "${BUILD_TIME.substring(0, 5)} ${BUILD_TIME.substring(11)}"
            } else {
                BUILD_TIME
            }
            return "v$VERSION_NAME (Build #$BUILD_NUMBER) • $shortTime"
        }

    /**
     * Assinatura para splash: "v2.5 • Build #25"
     */
    val splashSignature: String
        get() = "v$VERSION_NAME • Build #$BUILD_NUMBER"
}
