package com.esper.authapp.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LockScreenObserver(
    context: Context,
    private val observedUri: Uri,
    private val onStateChanged: suspend () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val contentResolver = context.applicationContext.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register() {
        contentResolver.registerContentObserver(observedUri, false, this)
    }

    fun unregister() {
        runCatching { contentResolver.unregisterContentObserver(this) }
        scope.cancel()
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        scope.launch { onStateChanged() }
    }
}
