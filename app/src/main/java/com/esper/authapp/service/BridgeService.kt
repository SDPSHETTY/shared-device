package com.esper.authapp.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.esper.authapp.config.AppConfig
import com.esper.authapp.esper.EsperApiClient
import com.esper.authapp.esper.EsperDeviceManager
import com.esper.authapp.util.Logger
import com.esper.authapp.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var esperDeviceManager: EsperDeviceManager
    private lateinit var esperApiClient: EsperApiClient


    override fun onCreate() {
        super.onCreate()
        AppConfig.init(this)
        AppConfig.logManagedConfigSnapshot("BridgeService start")
        AppConfig.logSecureConfigSnapshot("BridgeService start")
        NotificationHelper.ensureChannel(this)
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.buildForegroundNotification(this, getString(com.esper.authapp.R.string.notification_idle))
        )

        esperDeviceManager = EsperDeviceManager(this)
        esperApiClient = EsperApiClient()

        Logger.i(TAG, "Shared device authentication service ready")
        updateNotification("Shared device authentication ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null



    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.buildForegroundNotification(this, message)
        )
    }

    companion object {
        private const val TAG = "BridgeService"
        private const val ACTION_START = "com.esper.authapp.action.START"
        private const val ACTION_STOP = "com.esper.authapp.action.STOP"

        fun start(context: Context) {
            AppConfig.init(context)
            val intent = Intent(context, BridgeService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            AppConfig.init(context)
            context.startService(Intent(context, BridgeService::class.java).setAction(ACTION_STOP))
        }
    }
}
