package com.esper.authapp.config

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import com.esper.authapp.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object AppConfig {
    private const val TAG = "AppConfig"
    private const val PREFS_NAME = "zebra_esper_bridge_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_ENTERPRISE_ID = "enterprise_id"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_UUID = "uuid"
    private const val KEY_HOME_GROUP_ID = "home_group_id"
    private const val KEY_HOME_GROUP_NAME = "home_group_name"
    private const val KEY_ROLE_ONE_LABEL = "role_one_label"
    private const val KEY_ROLE_TWO_LABEL = "role_two_label"
    private const val KEY_ROLE_ONE_GROUP_ID = "role_one_group_id"
    private const val KEY_ROLE_TWO_GROUP_ID = "role_two_group_id"
    private const val KEY_ROLE_ONE_PASSWORD = "role_one_password"
    private const val KEY_ROLE_TWO_PASSWORD = "role_two_password"
    private const val KEY_ROLE_MAPPINGS = "role_mappings"
    private const val KEY_ZEBRA_LOCKSCREEN_URI = "zebra_lockscreen_uri"
    private const val KEY_ZEBRA_CURRENT_SESSION_URI = "zebra_current_session_uri"
    private const val KEY_ZEBRA_ROLE_FIELD = "zebra_role_field"
    private const val KEY_ZEBRA_USER_ID_FIELD = "zebra_user_id_field"
    private const val KEY_ZEBRA_EVENT_TYPE_FIELD = "zebra_event_type_field"
    private const val KEY_ZEBRA_LOGGED_IN_STATE_FIELD = "zebra_logged_in_state_field"
    private const val KEY_ZEBRA_LOGIN_STATE_VALUE = "zebra_login_state_value"
    private const val KEY_ZEBRA_LOGOUT_STATE_VALUE = "zebra_logout_state_value"

    // Session Management Keys
    private const val KEY_CURRENT_SESSION_ACTIVE = "current_session_active"
    private const val KEY_CURRENT_USER_ROLE = "current_user_role"
    private const val KEY_CURRENT_USER_ID = "current_user_id"
    private const val KEY_CURRENT_USER_NAME = "current_user_name"
    private const val KEY_SESSION_START_TIME = "session_start_time"
    private const val KEY_CURRENT_GROUP_ID = "current_group_id"

    // Security Enhancement Keys (Auto-hashing)
    private const val KEY_ROLE_ONE_PASSWORD_HASH_LOCAL = "role_one_password_hash_local"
    private const val KEY_ROLE_TWO_PASSWORD_HASH_LOCAL = "role_two_password_hash_local"
    private const val KEY_API_KEY_OBFUSCATED_LOCAL = "api_key_obfuscated_local"
    private const val KEY_LAST_MANAGED_PASSWORD_ONE = "last_managed_password_one"
    private const val KEY_LAST_MANAGED_PASSWORD_TWO = "last_managed_password_two"
    private const val KEY_LAST_MANAGED_API_KEY = "last_managed_api_key"

    private const val DEFAULT_BASE_URL = "https://espersalesdemo.esper.cloud"
    private const val DEFAULT_ZEBRA_LOCKSCREEN_URI = "content://com.zebra.mdna.els.provider/lockscreenstatus/state"
    private const val DEFAULT_ZEBRA_CURRENT_SESSION_URI = "content://com.zebra.mdna.els.provider/v2/currentsession"
    private const val DEFAULT_ZEBRA_ROLE_FIELD = "userRole"
    private const val DEFAULT_ZEBRA_USER_ID_FIELD = "userId"
    private const val DEFAULT_ZEBRA_EVENT_TYPE_FIELD = "eventType"
    private const val DEFAULT_ZEBRA_LOGGED_IN_STATE_FIELD = "userLoggedInState"
    private const val DEFAULT_ZEBRA_LOGIN_STATE_VALUE = "HIDDEN"
    private const val DEFAULT_ZEBRA_LOGOUT_STATE_VALUE = "SHOWN"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun appContext(): Context = requireNotNull(appContext) { "AppConfig.init(context) must be called first" }

    private fun prefs(): SharedPreferences = requireNotNull(prefs) { "AppConfig.init(context) must be called first" }

    fun saveConfiguration(
        apiKey: String,
        baseUrl: String,
        enterpriseId: String,
        deviceId: String,
        logoutGroupId: String,
        roleMappings: List<RoleGroupMapping>
    ) {
        prefs().edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_BASE_URL, baseUrl.trim().ifEmpty { DEFAULT_BASE_URL })
            .putString(KEY_ENTERPRISE_ID, enterpriseId.trim())
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .putString(KEY_HOME_GROUP_ID, logoutGroupId.trim())
            .putString(KEY_ROLE_MAPPINGS, JSONArray().apply {
                roleMappings.forEach { mapping ->
                    put(
                        JSONObject()
                            .put("role", mapping.role.trim())
                            .put("groupId", mapping.groupId.trim())
                    )
                }
            }.toString())
            .apply()
    }

    fun getLocalEsperApiKey(): String = prefs().getString(KEY_API_KEY, "") ?: ""

    fun getEsperApiKey(): String {
        // Check if we have obfuscated version locally
        val obfuscated = prefs().getString(KEY_API_KEY_OBFUSCATED_LOCAL, null)
        val lastManagedApiKey = prefs().getString(KEY_LAST_MANAGED_API_KEY, null)

        // Get current plaintext from managed config
        val currentManagedApiKey = getManagedValue(KEY_API_KEY)
        val localApiKey = getLocalEsperApiKey()
        val currentApiKey = currentManagedApiKey ?: localApiKey

        // If we have obfuscated version and the API key hasn't changed, use it
        if (obfuscated != null && currentApiKey == lastManagedApiKey) {
            return deobfuscateApiKey(obfuscated)
        }

        // If API key is available, auto-upgrade to obfuscated storage
        if (currentApiKey.isNotBlank()) {
            Logger.i(TAG, "Auto-upgrading API key to obfuscated storage")
            val obfuscatedKey = obfuscateApiKey(currentApiKey)

            // Store the obfuscated version and track the source
            prefs().edit()
                .putString(KEY_API_KEY_OBFUSCATED_LOCAL, obfuscatedKey)
                .putString(KEY_LAST_MANAGED_API_KEY, currentApiKey)
                .apply()

            return currentApiKey
        }

        return ""
    }

    fun isEsperApiKeyManaged(): Boolean = getManagedValue(KEY_API_KEY) != null

    fun getLocalEsperBaseUrl(): String = prefs().getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun getEsperBaseUrl(): String = getManagedValue(KEY_BASE_URL) ?: getLocalEsperBaseUrl()

    fun isEsperBaseUrlManaged(): Boolean = getManagedValue(KEY_BASE_URL) != null

    fun getLocalEnterpriseId(): String = prefs().getString(KEY_ENTERPRISE_ID, "") ?: ""

    fun getEnterpriseId(): String = getManagedValue(KEY_ENTERPRISE_ID) ?: getLocalEnterpriseId()

    fun isEnterpriseIdManaged(): Boolean = getManagedValue(KEY_ENTERPRISE_ID) != null

    fun getLocalDeviceId(): String = prefs().getString(KEY_DEVICE_ID, "") ?: ""

    fun getDeviceIdOverride(): String = getManagedValue(KEY_DEVICE_ID) ?: getManagedValue(KEY_UUID) ?: getLocalDeviceId()

    fun isDeviceIdManaged(): Boolean = getManagedValue(KEY_DEVICE_ID) != null || getManagedValue(KEY_UUID) != null

    fun getRoleOneLabel(): String = getManagedValue(KEY_ROLE_ONE_LABEL) ?: "Supervisor"

    fun getRoleTwoLabel(): String = getManagedValue(KEY_ROLE_TWO_LABEL) ?: "Associate"

    fun getRoleOnePassword(): String {
        // Check if we have a local hash first
        val localHash = prefs().getString(KEY_ROLE_ONE_PASSWORD_HASH_LOCAL, null)
        val lastManagedPassword = prefs().getString(KEY_LAST_MANAGED_PASSWORD_ONE, null)

        // Get current plaintext from managed config
        val currentManagedPassword = getManagedValue(KEY_ROLE_ONE_PASSWORD)

        // If we have a hash and the managed password hasn't changed, use the hash
        if (localHash != null && currentManagedPassword == lastManagedPassword) {
            return "hash:$localHash"
        }

        // If managed password is available, auto-upgrade to hash
        if (!currentManagedPassword.isNullOrBlank()) {
            Logger.i(TAG, "Auto-upgrading role one password to hash")
            val hash = hashPassword(currentManagedPassword)

            // Store the hash and track the source password
            prefs().edit()
                .putString(KEY_ROLE_ONE_PASSWORD_HASH_LOCAL, hash)
                .putString(KEY_LAST_MANAGED_PASSWORD_ONE, currentManagedPassword)
                .apply()

            return "hash:$hash"
        }

        return ""
    }

    fun getRoleTwoPassword(): String {
        // Check if we have a local hash first
        val localHash = prefs().getString(KEY_ROLE_TWO_PASSWORD_HASH_LOCAL, null)
        val lastManagedPassword = prefs().getString(KEY_LAST_MANAGED_PASSWORD_TWO, null)

        // Get current plaintext from managed config
        val currentManagedPassword = getManagedValue(KEY_ROLE_TWO_PASSWORD)

        // If we have a hash and the managed password hasn't changed, use the hash
        if (localHash != null && currentManagedPassword == lastManagedPassword) {
            return "hash:$localHash"
        }

        // If managed password is available, auto-upgrade to hash
        if (!currentManagedPassword.isNullOrBlank()) {
            Logger.i(TAG, "Auto-upgrading role two password to hash")
            val hash = hashPassword(currentManagedPassword)

            // Store the hash and track the source password
            prefs().edit()
                .putString(KEY_ROLE_TWO_PASSWORD_HASH_LOCAL, hash)
                .putString(KEY_LAST_MANAGED_PASSWORD_TWO, currentManagedPassword)
                .apply()

            return "hash:$hash"
        }

        return ""
    }

    fun getHomeGroupId(): String = prefs().getString(KEY_HOME_GROUP_ID, "") ?: ""

    fun getHomeGroupName(): String = prefs().getString(KEY_HOME_GROUP_NAME, "") ?: ""

    fun saveDiscoveredHomeGroup(groupId: String, groupName: String?) {
        val normalizedId = groupId.trim()
        if (normalizedId.isBlank()) {
            return
        }
        prefs().edit()
            .putString(KEY_HOME_GROUP_ID, normalizedId)
            .putString(KEY_HOME_GROUP_NAME, groupName?.trim().orEmpty())
            .apply()
    }

    fun getRoleOneGroupId(): String = getManagedValue(KEY_ROLE_ONE_GROUP_ID) ?: ""

    fun getRoleTwoGroupId(): String = getManagedValue(KEY_ROLE_TWO_GROUP_ID) ?: ""

    fun getZebraLockScreenUri(): String = getManagedOrDefault(KEY_ZEBRA_LOCKSCREEN_URI, DEFAULT_ZEBRA_LOCKSCREEN_URI)

    fun getZebraCurrentSessionUri(): String = getManagedOrDefault(KEY_ZEBRA_CURRENT_SESSION_URI, DEFAULT_ZEBRA_CURRENT_SESSION_URI)

    fun getZebraRoleField(): String = getManagedOrDefault(KEY_ZEBRA_ROLE_FIELD, DEFAULT_ZEBRA_ROLE_FIELD)

    fun getZebraUserIdField(): String = getManagedOrDefault(KEY_ZEBRA_USER_ID_FIELD, DEFAULT_ZEBRA_USER_ID_FIELD)

    fun getZebraEventTypeField(): String = getManagedOrDefault(KEY_ZEBRA_EVENT_TYPE_FIELD, DEFAULT_ZEBRA_EVENT_TYPE_FIELD)

    fun getZebraLoggedInStateField(): String = getManagedOrDefault(KEY_ZEBRA_LOGGED_IN_STATE_FIELD, DEFAULT_ZEBRA_LOGGED_IN_STATE_FIELD)

    fun getZebraLoginStateValue(): String = getManagedOrDefault(KEY_ZEBRA_LOGIN_STATE_VALUE, DEFAULT_ZEBRA_LOGIN_STATE_VALUE)

    fun getZebraLogoutStateValue(): String = getManagedOrDefault(KEY_ZEBRA_LOGOUT_STATE_VALUE, DEFAULT_ZEBRA_LOGOUT_STATE_VALUE)

    fun getRoleMappings(): List<RoleGroupMapping> {
        val raw = prefs().getString(KEY_ROLE_MAPPINGS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        RoleGroupMapping(
                            role = item.optString("role"),
                            groupId = item.optString("groupId")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getTargetGroupIdForRole(role: String?): String? {
        val normalizedRole = role?.trim()?.lowercase().orEmpty()
        if (normalizedRole.isEmpty()) {
            return null
        }
        return getRoleMappings().firstOrNull { it.role.trim().lowercase() == normalizedRole }?.groupId?.trim()
    }

    fun isApiConfigured(): Boolean {
        return getEsperApiKey().isNotBlank()
    }

    fun getManagedFieldCount(): Int {
        return listOf(
            isEsperApiKeyManaged()
        ).count { it }
    }

    fun logManagedConfigSnapshot(source: String) {
        Logger.i(
            TAG,
            "$source managed config -> apiKey=${isEsperApiKeyManaged()}, zebraOverrides=${hasManagedZebraOverrides()}"
        )
    }

    /**
     * Log secure configuration snapshot for reporting
     * This logs hashed/masked values instead of plaintext for security
     */
    fun logSecureConfigSnapshot(source: String) {
        val secureBundle = getSecureManagedRestrictionsForReporting()
        val roleOneStatus = secureBundle.getString(KEY_ROLE_ONE_PASSWORD)?.let {
            if (it.startsWith("hash:")) "SECURED_HASH" else "CONFIGURED"
        } ?: "NOT_SET"

        val roleTwoStatus = secureBundle.getString(KEY_ROLE_TWO_PASSWORD)?.let {
            if (it.startsWith("hash:")) "SECURED_HASH" else "CONFIGURED"
        } ?: "NOT_SET"

        val apiKeyStatus = secureBundle.getString(KEY_API_KEY)?.let {
            if (it.startsWith("obfuscated:")) "SECURED_OBFUSCATED" else "CONFIGURED"
        } ?: "NOT_SET"

        Logger.i(
            TAG,
            "$source SECURE config -> roleOne=$roleOneStatus, roleTwo=$roleTwoStatus, apiKey=$apiKeyStatus, zebraOverrides=${hasManagedZebraOverrides()}"
        )
    }

    // Session Management Functions
    fun startUserSession(role: String, userId: String = "", userName: String = "", groupId: String = "") {
        prefs().edit()
            .putBoolean(KEY_CURRENT_SESSION_ACTIVE, true)
            .putString(KEY_CURRENT_USER_ROLE, role)
            .putString(KEY_CURRENT_USER_ID, userId)
            .putString(KEY_CURRENT_USER_NAME, userName)
            .putString(KEY_CURRENT_GROUP_ID, groupId)
            .putLong(KEY_SESSION_START_TIME, System.currentTimeMillis())
            .apply()
        Logger.i(TAG, "Started session for role: $role, user: $userName")
    }

    fun endUserSession() {
        val editor = prefs().edit()
        editor.putBoolean(KEY_CURRENT_SESSION_ACTIVE, false)
        editor.remove(KEY_CURRENT_USER_ROLE)
        editor.remove(KEY_CURRENT_USER_ID)
        editor.remove(KEY_CURRENT_USER_NAME)
        editor.remove(KEY_CURRENT_GROUP_ID)
        editor.remove(KEY_SESSION_START_TIME)
        editor.apply()
        Logger.i(TAG, "Ended user session")
    }

    fun isSessionActive(): Boolean {
        return prefs().getBoolean(KEY_CURRENT_SESSION_ACTIVE, false)
    }

    fun getCurrentUserRole(): String {
        return prefs().getString(KEY_CURRENT_USER_ROLE, "") ?: ""
    }

    fun getCurrentUserId(): String {
        return prefs().getString(KEY_CURRENT_USER_ID, "") ?: ""
    }

    fun getCurrentUserName(): String {
        return prefs().getString(KEY_CURRENT_USER_NAME, "") ?: ""
    }

    fun getCurrentGroupId(): String {
        return prefs().getString(KEY_CURRENT_GROUP_ID, "") ?: ""
    }

    fun getSessionStartTime(): Long {
        return prefs().getLong(KEY_SESSION_START_TIME, 0)
    }

    fun getSessionDurationMinutes(): Long {
        val startTime = getSessionStartTime()
        return if (startTime > 0) {
            (System.currentTimeMillis() - startTime) / 60_000
        } else {
            0
        }
    }

    fun getSessionDurationFormatted(): String {
        val totalMinutes = getSessionDurationMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    private fun getManagedValue(key: String): String? {
        val value = managedRestrictions().getString(key)?.trim().orEmpty()
        return value.ifEmpty { null }
    }

    private fun getManagedOrDefault(key: String, defaultValue: String): String {
        return getManagedValue(key) ?: defaultValue
    }

    private fun hasManagedZebraOverrides(): Boolean {
        return listOf(
            KEY_ZEBRA_LOCKSCREEN_URI,
            KEY_ZEBRA_CURRENT_SESSION_URI,
            KEY_ZEBRA_ROLE_FIELD,
            KEY_ZEBRA_USER_ID_FIELD,
            KEY_ZEBRA_EVENT_TYPE_FIELD,
            KEY_ZEBRA_LOGGED_IN_STATE_FIELD,
            KEY_ZEBRA_LOGIN_STATE_VALUE,
            KEY_ZEBRA_LOGOUT_STATE_VALUE
        ).any { getManagedValue(it) != null }
    }

    private fun managedRestrictions(): Bundle {
        val restrictionsManager = appContext().getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
        return restrictionsManager?.applicationRestrictions ?: Bundle.EMPTY
    }

    /**
     * Get managed restrictions with sensitive values secured for reporting
     * This function returns a Bundle with passwords and API keys hashed/masked
     * for secure reporting back to management console
     */
    fun getSecureManagedRestrictionsForReporting(): Bundle {
        val originalBundle = managedRestrictions()
        val secureBundle = Bundle(originalBundle)

        // Replace sensitive values with hashed versions for reporting
        originalBundle.getString(KEY_ROLE_ONE_PASSWORD)?.let { plaintext ->
            if (plaintext.isNotBlank()) {
                val hash = hashPassword(plaintext)
                secureBundle.putString(KEY_ROLE_ONE_PASSWORD, "hash:$hash")
                Logger.i(TAG, "Secured role one password for reporting")
            }
        }

        originalBundle.getString(KEY_ROLE_TWO_PASSWORD)?.let { plaintext ->
            if (plaintext.isNotBlank()) {
                val hash = hashPassword(plaintext)
                secureBundle.putString(KEY_ROLE_TWO_PASSWORD, "hash:$hash")
                Logger.i(TAG, "Secured role two password for reporting")
            }
        }

        originalBundle.getString(KEY_API_KEY)?.let { plaintext ->
            if (plaintext.isNotBlank()) {
                val obfuscated = obfuscateApiKey(plaintext)
                secureBundle.putString(KEY_API_KEY, "obfuscated:${obfuscated.take(16)}...")
                Logger.i(TAG, "Secured API key for reporting")
            }
        }

        return secureBundle
    }

    /**
     * Get configuration status summary for secure reporting
     * Returns status indicators instead of actual sensitive values
     */
    fun getConfigStatusForReporting(): Bundle {
        val originalBundle = managedRestrictions()
        val statusBundle = Bundle()

        // Copy non-sensitive fields as-is
        originalBundle.getString(KEY_ROLE_ONE_LABEL)?.let {
            statusBundle.putString(KEY_ROLE_ONE_LABEL, it)
        }
        originalBundle.getString(KEY_ROLE_TWO_LABEL)?.let {
            statusBundle.putString(KEY_ROLE_TWO_LABEL, it)
        }
        originalBundle.getString(KEY_ROLE_ONE_GROUP_ID)?.let {
            statusBundle.putString(KEY_ROLE_ONE_GROUP_ID, it)
        }
        originalBundle.getString(KEY_ROLE_TWO_GROUP_ID)?.let {
            statusBundle.putString(KEY_ROLE_TWO_GROUP_ID, it)
        }

        // Replace sensitive fields with status indicators
        val roleOnePassword = originalBundle.getString(KEY_ROLE_ONE_PASSWORD)
        statusBundle.putString(KEY_ROLE_ONE_PASSWORD,
            if (roleOnePassword.isNullOrBlank()) "NOT_CONFIGURED" else "CONFIGURED_SECURELY"
        )

        val roleTwoPassword = originalBundle.getString(KEY_ROLE_TWO_PASSWORD)
        statusBundle.putString(KEY_ROLE_TWO_PASSWORD,
            if (roleTwoPassword.isNullOrBlank()) "NOT_CONFIGURED" else "CONFIGURED_SECURELY"
        )

        val apiKey = originalBundle.getString(KEY_API_KEY)
        statusBundle.putString(KEY_API_KEY,
            if (apiKey.isNullOrBlank()) "NOT_CONFIGURED" else "CONFIGURED_SECURELY"
        )

        Logger.i(TAG, "Generated secure config status for reporting")
        return statusBundle
    }

    // Security Enhancement Helper Functions

    fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            // Add device-specific salt for additional security
            digest.update(getDeviceSalt().toByteArray())
            val hashBytes = digest.digest(password.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logger.e(TAG, "Password hashing failed", e)
            password // Fallback to plaintext on error
        }
    }

    private fun obfuscateApiKey(apiKey: String): String {
        return try {
            val salt = getDeviceSalt()
            val keyBytes = apiKey.toByteArray()
            val saltBytes = salt.toByteArray()

            val obfuscated = keyBytes.mapIndexed { index, byte ->
                (byte.toInt() xor saltBytes[index % saltBytes.size].toInt()).toByte()
            }.toByteArray()

            Base64.encodeToString(obfuscated, Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.e(TAG, "API key obfuscation failed", e)
            apiKey // Fallback to plaintext on error
        }
    }

    private fun deobfuscateApiKey(obfuscatedKey: String): String {
        return try {
            val salt = getDeviceSalt()
            val obfuscatedBytes = Base64.decode(obfuscatedKey, Base64.DEFAULT)
            val saltBytes = salt.toByteArray()

            val deobfuscated = obfuscatedBytes.mapIndexed { index, byte ->
                (byte.toInt() xor saltBytes[index % saltBytes.size].toInt()).toByte()
            }.toByteArray()

            String(deobfuscated)
        } catch (e: Exception) {
            Logger.e(TAG, "API key deobfuscation failed", e)
            ""
        }
    }

    private fun getDeviceSalt(): String {
        return try {
            // Use device-specific information as salt for additional security
            "${android.os.Build.SERIAL}_${appContext().packageName}".take(32)
        } catch (e: Exception) {
            // Fallback salt if device info is not available
            "default_salt_zebra_esper_bridge"
        }
    }
}
