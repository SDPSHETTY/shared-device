package com.esper.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.esper.authapp.config.AppConfig
import com.esper.authapp.service.BridgeService
import com.esper.authapp.util.Logger

class ManagedConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
            return
        }

        AppConfig.init(context)
        AppConfig.logManagedConfigSnapshot("Restrictions changed")

        if (AppConfig.isApiConfigured()) {
            Logger.i(TAG, "Managed config complete - starting bridge service")
            BridgeService.start(context)
        } else {
            Logger.i(TAG, "Managed config updated but required API key is still missing")
        }
    }

    companion object {
        private const val TAG = "ManagedConfigReceiver"
    }
}
