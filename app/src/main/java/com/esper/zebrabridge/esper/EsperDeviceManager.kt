package com.esper.authapp.esper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.esper.authapp.util.Logger
import io.esper.devicesdk.EsperDeviceSDK
import io.esper.devicesdk.models.EsperDeviceInfo
import io.esper.devicesdk.models.ProvisionInfo
import java.lang.Void
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class EsperDeviceManager(context: Context) {
    private val appContext = context.applicationContext
    private val sdk = EsperDeviceSDK.getInstance(context.applicationContext)

    suspend fun resolveRuntimeInfo(
        apiKey: String,
        fallbackBaseUrl: String? = null,
        fallbackEnterpriseId: String? = null,
        fallbackDeviceId: String? = null
    ): EsperRuntimeInfo {
        require(apiKey.isNotBlank()) { "Esper API key is required" }

        val directProvisionedInfo = runCatching { getProvisionedInfoOrNull() }.getOrNull()
        val directDeviceId = runCatching { getSdkDeviceId() }.getOrNull()

        if (directProvisionedInfo != null) {
            return EsperRuntimeInfo(
                tenantConfig = EsperTenantConfig(
                    apiKey = apiKey,
                    baseUrl = directProvisionedInfo.apiEndpoint,
                    enterpriseId = directProvisionedInfo.tenantUuid
                ),
                deviceId = directProvisionedInfo.deviceUuid.ifBlank {
                    directDeviceId?.takeIf { it.isNotBlank() }
                        ?: fallbackDeviceId?.trim().orEmpty()
                }.ifBlank {
                    error("Esper device UUID is unavailable from SDK")
                }
            )
        }

        val sdkActivated = isActivated()
        if (!sdkActivated) {
            require(isNetworkAvailable()) { "Network connection is required to finish sign-in." }
            Logger.i(TAG, "Esper SDK runtime info unavailable before activation - attempting activation")
            ensureActivated(apiKey)
        } else {
            Logger.i(TAG, "Esper SDK already activated - retrying runtime lookup")
        }

        val activatedProvisionedInfo = getProvisionedInfoOrNull()
        val activatedDeviceId = runCatching { getSdkDeviceId() }.getOrNull()

        val baseUrl = activatedProvisionedInfo?.apiEndpoint?.takeIf { it.isNotBlank() }
            ?: fallbackBaseUrl?.trim().orEmpty()
        val enterpriseId = activatedProvisionedInfo?.tenantUuid?.takeIf { it.isNotBlank() }
            ?: fallbackEnterpriseId?.trim().orEmpty()
        val deviceId = activatedProvisionedInfo?.deviceUuid?.takeIf { it.isNotBlank() }
            ?: activatedDeviceId?.takeIf { it.isNotBlank() }
            ?: fallbackDeviceId?.trim().orEmpty()

        require(baseUrl.isNotBlank()) { "Esper API endpoint is unavailable from SDK" }
        require(enterpriseId.isNotBlank()) { "Esper tenant UUID is unavailable from SDK" }
        require(deviceId.isNotBlank()) { "Esper device UUID is unavailable from SDK" }

        return EsperRuntimeInfo(
            tenantConfig = EsperTenantConfig(
                apiKey = apiKey,
                baseUrl = baseUrl,
                enterpriseId = enterpriseId
            ),
            deviceId = deviceId
        )
    }

    suspend fun getTenantConfig(
        apiKey: String,
        fallbackBaseUrl: String? = null,
        fallbackEnterpriseId: String? = null
    ): EsperTenantConfig {
        val provisionedInfo = getProvisionedInfoOrNull()
        val baseUrl = provisionedInfo?.apiEndpoint?.takeIf { it.isNotBlank() }
            ?: fallbackBaseUrl?.trim().orEmpty()
        val enterpriseId = provisionedInfo?.tenantUuid?.takeIf { it.isNotBlank() }
            ?: fallbackEnterpriseId?.trim().orEmpty()

        require(apiKey.isNotBlank()) { "Esper API key is required" }
        require(baseUrl.isNotBlank()) { "Esper API endpoint is unavailable from SDK" }
        require(enterpriseId.isNotBlank()) { "Esper tenant UUID is unavailable from SDK" }

        return EsperTenantConfig(
            apiKey = apiKey,
            baseUrl = baseUrl,
            enterpriseId = enterpriseId
        )
    }

    suspend fun ensureActivated(activationToken: String?) {
        if (isActivated()) {
            return
        }
        val token = activationToken?.trim().orEmpty()
        require(token.isNotEmpty()) { "Esper SDK activation token is required before using the SDK" }
        awaitCallback<Void> { callback -> sdk.activateSDK(token, callback) }
    }

    suspend fun getDeviceId(activationToken: String? = null): String {
        runCatching { getProvisionedDeviceId() }
            .getOrNull()
            ?.let { provisionedId ->
                runCatching { getSdkDeviceId() }
                    .onFailure { Logger.e(TAG, "Direct sdk.getEsperDeviceInfo() failed during diagnostics", it) }
                return provisionedId
            }

        val directSdkAttempt = runCatching { getSdkDeviceId() }
        directSdkAttempt.getOrNull()?.let { return it }

        val token = activationToken?.trim().orEmpty()
        if (token.isNotEmpty()) {
            return runCatching {
                ensureActivated(token)
                getSdkDeviceId()
            }.getOrElse { activationFailure ->
                val initialFailure = directSdkAttempt.exceptionOrNull()?.message
                val activationMessage = activationFailure.message.orEmpty()
                error(
                    buildString {
                        append("Unable to get Esper device info directly")
                        if (!initialFailure.isNullOrBlank()) {
                            append(": ")
                            append(initialFailure)
                        }
                        if (activationMessage.isNotBlank()) {
                            append(". SDK activation also failed: ")
                            append(activationMessage)
                        }
                    }
                )
            }
        }

        throw directSdkAttempt.exceptionOrNull() ?: error("Esper SDK returned no device identifier")
    }

    private suspend fun getProvisionedInfoOrNull(): EsperProvisionedInfo? {
        val info = awaitCallback<ProvisionInfo> { callback -> sdk.getProvisionInfo(callback) } ?: return null
        Logger.i(
            TAG,
            "Provision info -> apiEndpoint=${info.apiEndpoint}, tenantUUID=${info.tenantUUID}, deviceUUID=${info.deviceUUID}, complete=${info.complete}"
        )
        val apiEndpoint = info.apiEndpoint?.trim().orEmpty()
        val tenantUuid = info.tenantUUID?.trim().orEmpty()
        val deviceUuid = info.deviceUUID?.trim().orEmpty()
        if (apiEndpoint.isBlank() || tenantUuid.isBlank() || deviceUuid.isBlank()) {
            return null
        }
        return EsperProvisionedInfo(
            apiEndpoint = apiEndpoint,
            tenantUuid = tenantUuid,
            deviceUuid = deviceUuid
        )
    }

    private suspend fun getProvisionedDeviceId(): String? {
        return getProvisionedInfoOrNull()?.deviceUuid
    }

    private suspend fun getSdkDeviceId(): String {
        val info = awaitCallback<EsperDeviceInfo> { callback -> sdk.getEsperDeviceInfo(callback) }
            ?: error("Esper SDK returned no device info")
        Logger.i(
            TAG,
            "EsperDeviceInfo -> apiLevel=${sdk.apiLevel}, deviceId=${info.deviceId}, serialNo=${info.serialNo}, imei1=${info.imei1}, imei2=${info.imei2}, wifiMacAddress=${info.wifiMacAddress}, uuid=${info.uuid}"
        )
        return info.uuid?.takeIf { it.isNotBlank() }
            ?: info.deviceId?.takeIf { it.isNotBlank() }
            ?: error("Esper SDK returned no device identifier")
    }

    private suspend fun isActivated(): Boolean {
        val active = awaitCallback<Boolean> { callback -> sdk.isActivated(callback) } == true
        Logger.i(TAG, "SDK activation status -> active=$active, apiLevel=${sdk.apiLevel}")
        return active
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun <T> awaitCallback(invoke: (EsperDeviceSDK.Callback<T>) -> Unit): T? {
        return suspendCancellableCoroutine { continuation ->
            invoke(
                object : EsperDeviceSDK.Callback<T> {
                    override fun onResponse(response: T?) {
                        if (continuation.isActive) {
                            continuation.resume(response)
                        }
                    }

                    override fun onFailure(t: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(t)
                        }
                    }
                }
            )
        }
    }

    companion object {
        private const val TAG = "EsperDeviceManager"
    }
}
