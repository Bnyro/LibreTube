package com.github.libretube

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.NotificationHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.helpers.ShortcutHelper
import com.github.libretube.util.ExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibreTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        /**
         * Initialize the needed notification channels for DownloadService and BackgroundMode
         */
        initializeNotificationChannels()

        /**
         * Initialize the [PreferenceHelper]
         */
        PreferenceHelper.initialize(applicationContext)

        /**
         * Set the api and the auth api url
         */
        ImageHelper.initializeImageLoader(this)

        /**
         * Initialize the notification listener in the background
         */
        NotificationHelper.enqueueWork(
            context = this,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        )

        /**
         * Fetch the image proxy URL for local playlists and the watch history
         */
        fetchInstanceConfig()

        /**
         * Handler for uncaught exceptions
         */
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        val exceptionHandler = ExceptionHandler(defaultExceptionHandler)
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)

        /**
         * Dynamically create App Shortcuts
         */
        ShortcutHelper.createShortcuts(this)
    }

    fun fetchInstanceConfig() {
        val isAuthSameApi = RetrofitInstance.apiUrl == RetrofitInstance.authUrl

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val config = RetrofitInstance.api.getConfig()
                config.imageProxyUrl?.let {
                    PreferenceHelper.putString(PreferenceKeys.IMAGE_PROXY_URL, it)
                }
                if (isAuthSameApi) PreferenceHelper
                    .putStringSet(
                        PreferenceKeys.INSTANCE_OIDC_PROVIDERS,
                        config.oidcProviders.toSet()
                    )
            }
            if (!isAuthSameApi) runCatching {
                val config = RetrofitInstance.authApi.getConfig()
                PreferenceHelper
                    .putStringSet(
                        PreferenceKeys.INSTANCE_OIDC_PROVIDERS,
                        config.oidcProviders.toSet()
                    )
            }
        }
    }

    /**
     * Initializes the required notification channels for the app.
     */
    private fun initializeNotificationChannels() {
        val downloadChannel = NotificationChannelCompat.Builder(
            PLAYLIST_DOWNLOAD_ENQUEUE_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(getString(R.string.download_playlist))
            .setDescription(getString(R.string.enqueue_playlist_description))
            .build()
        val playlistDownloadEnqueueChannel = NotificationChannelCompat.Builder(
            DOWNLOAD_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(getString(R.string.download_channel_name))
            .setDescription(getString(R.string.download_channel_description))
            .build()
        val playerChannel = NotificationChannelCompat.Builder(
            PLAYER_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(getString(R.string.player_channel_name))
            .setDescription(getString(R.string.player_channel_description))
            .build()
        val pushChannel = NotificationChannelCompat.Builder(
            PUSH_CHANNEL_NAME,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName(getString(R.string.push_channel_name))
            .setDescription(getString(R.string.push_channel_description))
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannelsCompat(
            listOf(
                downloadChannel,
                playlistDownloadEnqueueChannel,
                pushChannel,
                playerChannel
            )
        )
    }

    companion object {
        lateinit var instance: LibreTubeApp

        const val DOWNLOAD_CHANNEL_NAME = "download_service"
        const val PLAYLIST_DOWNLOAD_ENQUEUE_CHANNEL_NAME = "playlist_download_enqueue"
        const val PLAYER_CHANNEL_NAME = "player_mode"
        const val PUSH_CHANNEL_NAME = "notification_worker"
    }
}
