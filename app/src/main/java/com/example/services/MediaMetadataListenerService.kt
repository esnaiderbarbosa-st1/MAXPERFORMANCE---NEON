package com.example.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Serviço leve para leitura universal do título da música e artista em reprodução
 * (YouTube Music, Spotify, Deezer, Apple Music, Samsung Music, etc.) via MediaSessionManager
 * e notificações de transporte de mídia.
 */
class MediaMetadataListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val registeredControllers = mutableMapOf<String, Pair<MediaController, MediaController.Callback>>()

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        inspectMediaControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        setupMediaSessionMonitoring()
        refreshFromActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
        }
        cleanupControllers()
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        cleanupControllers()
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        extractMetadataFromNotification(sbn)
        refreshMediaSessions(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshMediaSessions(this)
    }

    private fun setupMediaSessionMonitoring() {
        runCatching {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            mediaSessionManager = manager
            val componentName = ComponentName(this, MediaMetadataListenerService::class.java)
            manager.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            val controllers = manager.getActiveSessions(componentName)
            inspectMediaControllers(controllers)
        }
    }

    private fun inspectMediaControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return
        cleanupControllers()

        // Prioriza a sessão que está efetivamente tocando (STATE_PLAYING), e desempata priorizando o Spotify (com.spotify.music)
        val sortedControllers = controllers.sortedByDescending { ctrl ->
            val stateScore = when (ctrl.playbackState?.state) {
                PlaybackState.STATE_PLAYING -> 20
                PlaybackState.STATE_BUFFERING, PlaybackState.STATE_PAUSED -> 10
                else -> 0
            }
            val pkgBonus = if (ctrl.packageName.equals(SPOTIFY_PACKAGE, ignoreCase = true)) 5 else 0
            stateScore + pkgBonus
        }

        for (ctrl in sortedControllers) {
            val pkg = ctrl.packageName ?: continue
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    extractFromController(ctrl)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    extractFromController(ctrl)
                }
            }
            runCatching {
                ctrl.registerCallback(callback)
                registeredControllers[pkg] = ctrl to callback
            }
        }

        // Extrai imediatamente da sessão prioritária
        for (ctrl in sortedControllers) {
            if (extractFromController(ctrl)) {
                break
            }
        }
    }

    private fun extractFromController(ctrl: MediaController): Boolean {
        val metadata = ctrl.metadata
        val pbState = ctrl.playbackState
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)

        if (!title.isNullOrBlank()) {
            RunTracker.updateMediaTrackInfo(
                track = title,
                artist = artist,
                isPlaying = isPlaying,
                sourcePackage = ctrl.packageName
            )
            return true
        }
        return false
    }

    private fun refreshFromActiveNotifications() {
        runCatching {
            val activeList = activeNotifications ?: return
            for (sbn in activeList) {
                if (extractMetadataFromNotification(sbn)) {
                    break
                }
            }
        }
    }

    private fun extractMetadataFromNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: return false
        val pkg = sbn.packageName ?: ""

        val hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val isTransportCategory = notification.category == Notification.CATEGORY_TRANSPORT
        val isKnownMusicPackage = KNOWN_MUSIC_PACKAGES.any { pkg.contains(it, ignoreCase = true) }

        if (!hasMediaSession && !isTransportCategory && !isKnownMusicPackage) {
            return false
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
        val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        if (!title.isNullOrBlank() && !title.equals("Spotify", ignoreCase = true) && !title.equals("YouTube Music", ignoreCase = true)) {
            RunTracker.updateMediaTrackInfo(
                track = title,
                artist = artist,
                isPlaying = null,
                sourcePackage = pkg
            )
            return true
        }
        return false
    }

    private fun cleanupControllers() {
        for ((_, pair) in registeredControllers) {
            runCatching { pair.first.unregisterCallback(pair.second) }
        }
        registeredControllers.clear()
    }

    companion object {
        @Volatile
        private var instance: MediaMetadataListenerService? = null

        const val SPOTIFY_PACKAGE = "com.spotify.music"

        private val KNOWN_MUSIC_PACKAGES = listOf(
            "spotify",
            "deezer",
            "youtube.music",
            "music",
            "mp3",
            "tidal",
            "soundcloud",
            "apple.android.music",
            "poweramp",
            "musicolet"
        )

        /**
         * Verifica se o usuário já concedeu permissão de leitura de notificações/mídia para o app.
         */
        fun isNotificationListenerEnabled(context: Context): Boolean {
            return runCatching {
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }.getOrDefault(false)
        }

        /**
         * Envia comando de transporte diretamente para a MediaSession ativa, priorizando o Spotify (com.spotify.music)
         * quando nenhuma outra sessão estiver ativamente em STATE_PLAYING.
         * Retorna true se um controlador válido recebeu o comando.
         */
        fun dispatchCommandToPreferredController(context: Context, keyCode: Int): Boolean {
            if (!isNotificationListenerEnabled(context)) return false
            return runCatching {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return false
                val componentName = ComponentName(context, MediaMetadataListenerService::class.java)
                val controllers = manager.getActiveSessions(componentName)
                if (controllers.isNullOrEmpty()) return false

                // 1. Se algum player já está efetivamente tocando (STATE_PLAYING), controla ele (priorizando Spotify se empatar)
                val playingCtrl = controllers
                    .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    .maxByOrNull { if (it.packageName.equals(SPOTIFY_PACKAGE, ignoreCase = true)) 1 else 0 }

                // 2. Caso contrário, busca exclusivamente a sessão do Spotify se ela existir na memória;
                //    só usa outro controlador se o Spotify não estiver instalado no aparelho.
                val spotifyInstalled = runCatching {
                    context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE) != null
                }.getOrDefault(false)

                val targetCtrl = playingCtrl
                    ?: controllers.firstOrNull { it.packageName.equals(SPOTIFY_PACKAGE, ignoreCase = true) }
                    ?: if (!spotifyInstalled) controllers.firstOrNull() else null

                if (targetCtrl == null) return false

                val isCurrentlyPlaying = targetCtrl.playbackState?.state == PlaybackState.STATE_PLAYING
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> targetCtrl.transportControls.skipToPrevious()
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> targetCtrl.transportControls.skipToNext()
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> targetCtrl.transportControls.play()
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> targetCtrl.transportControls.pause()
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (isCurrentlyPlaying) {
                            targetCtrl.transportControls.pause()
                        } else {
                            targetCtrl.transportControls.play()
                        }
                    }
                    else -> return false
                }
                true
            }.getOrDefault(false)
        }

        /**
         * Força a leitura imediata das MediaSessions e notificações ativas (ex: ao abrir o HUD ou voltar das Configurações).
         */
        fun refreshMediaSessions(context: Context) {
            if (!isNotificationListenerEnabled(context)) return
            runCatching {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
                val componentName = ComponentName(context, MediaMetadataListenerService::class.java)
                val controllers = manager.getActiveSessions(componentName)
                if (!controllers.isNullOrEmpty()) {
                    val sorted = controllers.sortedByDescending { ctrl ->
                        val playingScore = if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) 10 else 0
                        val spotifyScore = if (ctrl.packageName.equals(SPOTIFY_PACKAGE, ignoreCase = true)) 5 else 0
                        playingScore + spotifyScore
                    }
                    for (ctrl in sorted) {
                        val metadata = ctrl.metadata
                        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                        val isPlaying = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
                        if (!title.isNullOrBlank()) {
                            RunTracker.updateMediaTrackInfo(
                                track = title,
                                artist = artist,
                                isPlaying = isPlaying,
                                sourcePackage = ctrl.packageName
                            )
                            return
                        }
                    }
                }
                instance?.refreshFromActiveNotifications()
            }
        }
    }
}
