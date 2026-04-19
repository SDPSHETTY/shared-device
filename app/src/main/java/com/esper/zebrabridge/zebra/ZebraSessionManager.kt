package com.esper.authapp.zebra

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.esper.authapp.config.AppConfig
import com.esper.authapp.util.Logger
import org.json.JSONObject

class ZebraSessionManager(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver

    val lockScreenStateUri: Uri = Uri.parse(AppConfig.getZebraLockScreenUri())
    private val currentSessionUri: Uri = Uri.parse(AppConfig.getZebraCurrentSessionUri())

    fun queryLockScreenState(): LockScreenState? {
        return queryFirstText(lockScreenStateUri)
            ?.trim()
            ?.uppercase()
            ?.let { value ->
                when (value) {
                    AppConfig.getZebraLoginStateValue().uppercase() -> LockScreenState.HIDDEN
                    AppConfig.getZebraLogoutStateValue().uppercase() -> LockScreenState.SHOWN
                    else -> null
                }
            }
    }

    fun queryCurrentSession(): UserSession? {
        val rawJson = queryFirstText(currentSessionUri) ?: return null
        return runCatching {
            val json = JSONObject(rawJson)
            UserSession(
                eventType = json.optString(AppConfig.getZebraEventTypeField()).ifBlank { null },
                userId = json.optString(AppConfig.getZebraUserIdField()).ifBlank { null },
                userRole = json.optString(AppConfig.getZebraRoleField()).ifBlank { null },
                userLoggedInState = json.optString(AppConfig.getZebraLoggedInStateField()).ifBlank { null },
                rawJson = rawJson
            )
        }.onFailure {
            Logger.e(TAG, "Failed to parse Zebra session JSON: $rawJson", it)
        }.getOrNull()
    }

    private fun queryFirstText(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.extractFirstText()
            }
        }.onFailure {
            Logger.e(TAG, "Failed to query Zebra provider for $uri", it)
        }.getOrNull()
    }

    private fun Cursor.extractFirstText(): String? {
        if (!moveToFirst()) {
            return null
        }
        for (columnIndex in 0 until columnCount) {
            val value = runCatching { getString(columnIndex) }.getOrNull()
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    companion object {
        private const val TAG = "ZebraSessionManager"
    }
}
