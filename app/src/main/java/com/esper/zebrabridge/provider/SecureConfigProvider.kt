package com.esper.authapp.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.esper.authapp.config.AppConfig
import com.esper.authapp.util.Logger

/**
 * Secure Content Provider for reporting app configuration status
 * Returns only hashed/masked values instead of plaintext passwords and API keys
 * This provider can be queried by management systems for secure status reporting
 */
class SecureConfigProvider : ContentProvider() {

    companion object {
        private const val TAG = "SecureConfigProvider"
        private const val AUTHORITY = "com.esper.authapp.secureconfigprovider"

        // URI patterns
        private const val CONFIG_STATUS = 1
        private const val CONFIG_SECURE = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "config/status", CONFIG_STATUS)
            addURI(AUTHORITY, "config/secure", CONFIG_SECURE)
        }

        // Public URIs for external access
        val CONFIG_STATUS_URI: Uri = Uri.parse("content://$AUTHORITY/config/status")
        val CONFIG_SECURE_URI: Uri = Uri.parse("content://$AUTHORITY/config/secure")
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        context?.let { ctx ->
            AppConfig.init(ctx)

            return when (uriMatcher.match(uri)) {
                CONFIG_STATUS -> {
                    Logger.i(TAG, "Providing secure config status")
                    createConfigStatusCursor()
                }
                CONFIG_SECURE -> {
                    Logger.i(TAG, "Providing secure config with hashed values")
                    createSecureConfigCursor()
                }
                else -> {
                    Logger.e(TAG, "Unknown URI: $uri")
                    null
                }
            }
        }
        return null
    }

    private fun createConfigStatusCursor(): Cursor {
        val cursor = MatrixCursor(arrayOf("key", "value", "status"))
        val statusBundle = AppConfig.getConfigStatusForReporting()

        for (key in statusBundle.keySet()) {
            val value = statusBundle.getString(key) ?: ""
            val status = when {
                value == "CONFIGURED_SECURELY" -> "secured"
                value == "NOT_CONFIGURED" -> "missing"
                else -> "configured"
            }
            cursor.addRow(arrayOf(key, value, status))
        }

        return cursor
    }

    private fun createSecureConfigCursor(): Cursor {
        val cursor = MatrixCursor(arrayOf("key", "value", "type"))
        val secureBundle = AppConfig.getSecureManagedRestrictionsForReporting()

        for (key in secureBundle.keySet()) {
            val value = secureBundle.getString(key) ?: ""
            val type = when {
                value.startsWith("hash:") -> "password_hash"
                value.startsWith("obfuscated:") -> "api_key_obfuscated"
                else -> "plain"
            }
            cursor.addRow(arrayOf(key, value, type))
        }

        return cursor
    }

    // These methods are not supported for security reasons
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun getType(uri: Uri): String? = "vnd.android.cursor.dir/vnd.esper.secureconfig"
}