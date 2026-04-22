package com.esper.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.esper.authapp.config.AppConfig
import com.esper.authapp.service.BridgeService
import com.esper.authapp.util.Logger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        AppConfig.init(context)
        if (!AppConfig.isApiConfigured()) {
            Logger.i(TAG, "Skipping auto-start because configuration is incomplete")
            return
        }
        Logger.i(TAG, "Boot completed - starting bridge service")
        BridgeService.start(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
