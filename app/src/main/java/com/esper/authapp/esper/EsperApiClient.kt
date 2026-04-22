package com.esper.authapp.esper

import com.esper.authapp.util.Logger
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class EsperApiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun moveDeviceToGroup(
        config: EsperTenantConfig,
        deviceId: String,
        destinationGroupId: String
    ) = withContext(Dispatchers.IO) {
        val url = buildMoveDeviceUrl(config.baseUrl, config.enterpriseId, destinationGroupId)
        val requestJson = JSONObject()
            .put("device_ids", JSONArray().put(deviceId))
            .toString()
        val requestBody = requestJson.toRequestBody(JSON_MEDIA_TYPE)

        Logger.i(TAG, "Esper device move request -> PATCH $url body=$requestJson")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .patch(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            Logger.i(TAG, "Esper device move response -> ${response.code} body=$responseBody")
            if (!response.isSuccessful) {
                throw IOException("Esper move API failed with ${response.code}: $responseBody")
            }
        }
    }

    suspend fun fetchDeviceState(
        config: EsperTenantConfig,
        deviceId: String
    ): EsperDeviceState = withContext(Dispatchers.IO) {
        val url = buildDeviceDetailsUrl(config.baseUrl, config.enterpriseId, deviceId)
        Logger.i(TAG, "Esper device details request -> GET $url")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            Logger.i(TAG, "Esper device details response -> ${response.code} body=$responseBody")
            if (!response.isSuccessful) {
                throw IOException("Esper device details API failed with ${response.code}: $responseBody")
            }
            parseDeviceState(responseBody)
        }
    }

    private fun buildMoveDeviceUrl(baseUrl: String, enterpriseId: String, groupId: String): String {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        return "$normalizedBase/enterprise/$enterpriseId/devicegroup/$groupId/?action=add"
    }

    private fun buildDeviceDetailsUrl(baseUrl: String, enterpriseId: String, deviceId: String): String {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        return "$normalizedBase/enterprise/$enterpriseId/device/$deviceId/"
    }

    private fun parseDeviceState(responseBody: String): EsperDeviceState {
        val root = JSONObject(responseBody)
        val groupsArray = root.optJSONArray("groups") ?: JSONArray()
        val groups = buildList {
            for (index in 0 until groupsArray.length()) {
                when (val item = groupsArray.opt(index)) {
                    is JSONObject -> {
                        val id = item.optString("id").ifBlank {
                            extractUuidFromUrl(item.optString("url")).orEmpty()
                        }
                        if (id.isNotBlank()) {
                            add(EsperDeviceGroup(id = id, name = item.optString("name").ifBlank { null }))
                        }
                    }
                    is String -> {
                        val id = extractUuidFromUrl(item) ?: item
                        if (id.isNotBlank()) {
                            add(EsperDeviceGroup(id = id, name = null))
                        }
                    }
                }
            }
        }
        return EsperDeviceState(
            groups = groups,
            currentBlueprintId = root.optString("current_blueprint_id").ifBlank { null }
        )
    }

    private fun extractUuidFromUrl(value: String?): String? {
        val trimmed = value?.trim().orEmpty().trimEnd('/')
        if (trimmed.isBlank()) {
            return null
        }
        val lastSegment = trimmed.substringAfterLast('/')
        return if (lastSegment.contains('-')) lastSegment else null
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().ifEmpty { error("Esper base URL is empty") }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = URI(withScheme)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return withScheme.trimEnd('/')
        val normalizedHost = if (host.contains("-api.")) host else host.replaceFirst(".esper.cloud", "-api.esper.cloud")
        val normalizedPath = (uri.path ?: "").trimEnd('/')
        val pathWithApi = if (normalizedPath.endsWith("/api")) normalizedPath else "$normalizedPath/api"
        return "$scheme://$normalizedHost${pathWithApi.trimEnd('/')}"
    }

    companion object {
        private const val TAG = "EsperApiClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
