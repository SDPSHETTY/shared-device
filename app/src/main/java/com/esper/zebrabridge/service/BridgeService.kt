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
import com.esper.authapp.esper.EsperTenantConfig
import com.esper.authapp.util.Logger
import com.esper.authapp.util.NotificationHelper
import com.esper.authapp.zebra.LockScreenState
import com.esper.authapp.zebra.ZebraSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var zebraSessionManager: ZebraSessionManager
    private lateinit var esperDeviceManager: EsperDeviceManager
    private lateinit var esperApiClient: EsperApiClient
    private lateinit var lockScreenObserver: LockScreenObserver

    private var lastActionMessage: String = ""
    private var lastFingerprint: String? = null

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

        zebraSessionManager = ZebraSessionManager(this)
        esperDeviceManager = EsperDeviceManager(this)
        esperApiClient = EsperApiClient()
        lockScreenObserver = LockScreenObserver(this, zebraSessionManager.lockScreenStateUri) {
            processCurrentState()
        }
        lockScreenObserver.register()
        scope.launch { processCurrentState() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        lockScreenObserver.unregister()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun processCurrentState() {
        val state = zebraSessionManager.queryLockScreenState()
        Logger.i(TAG, "Observed lockscreen state: $state")
        when (state) {
            LockScreenState.HIDDEN -> handleLogin()
            LockScreenState.SHOWN -> handleLogout()
            null -> updateNotification("Zebra lockscreen state unavailable")
        }
    }

    private suspend fun handleLogin() {
        val session = zebraSessionManager.queryCurrentSession()
        val role = session?.userRole?.trim().orEmpty()
        if (role.isEmpty()) {
            updateNotification("Login detected but Zebra role is unavailable")
            return
        }
        val targetGroupId = AppConfig.getTargetGroupIdForRole(role)
        if (targetGroupId.isNullOrBlank()) {
            updateNotification("No Esper group mapping for role $role")
            return
        }

        val fingerprint = "login:$role:$targetGroupId"
        if (lastFingerprint == fingerprint) {
            updateNotification("Role $role already synced")
            return
        }
        moveDeviceToGroup(targetGroupId, "login role $role")
        lastFingerprint = fingerprint
    }

    private suspend fun handleLogout() {
        val targetGroupId = AppConfig.getHomeGroupId().trim()
        if (targetGroupId.isEmpty()) {
            updateNotification("Logout detected but no shared profile was discovered")
            return
        }
        val fingerprint = "logout:$targetGroupId"
        if (lastFingerprint == fingerprint) {
            updateNotification("Logout group already synced")
            return
        }
        moveDeviceToGroup(targetGroupId, "logout")
        lastFingerprint = fingerprint
    }

    private suspend fun moveDeviceToGroup(targetGroupId: String, reason: String) {
        if (!AppConfig.isApiConfigured()) {
            updateNotification("Bridge configuration incomplete")
            return
        }

        updateNotification(getString(com.esper.authapp.R.string.notification_processing))
        runCatching {
            val runtimeInfo = esperDeviceManager.resolveRuntimeInfo(
                apiKey = AppConfig.getEsperApiKey(),
                fallbackBaseUrl = AppConfig.getEsperBaseUrl(),
                fallbackEnterpriseId = AppConfig.getEnterpriseId(),
                fallbackDeviceId = AppConfig.getDeviceIdOverride()
            )
            esperApiClient.moveDeviceToGroup(
                config = runtimeInfo.tenantConfig,
                deviceId = runtimeInfo.deviceId,
                destinationGroupId = targetGroupId
            )
            lastActionMessage = "$reason -> $targetGroupId"
            Logger.i(TAG, "Moved device ${runtimeInfo.deviceId} to group $targetGroupId for $reason")
            updateNotification(getString(com.esper.authapp.R.string.notification_last_action, lastActionMessage))
        }.onFailure {
            Logger.e(TAG, "Failed to move device for $reason", it)
            updateNotification("Move failed: ${it.message}")
        }
    }

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
