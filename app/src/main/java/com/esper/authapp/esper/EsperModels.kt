package com.esper.authapp.esper

data class EsperTenantConfig(
    val apiKey: String,
    val baseUrl: String,
    val enterpriseId: String
)

data class EsperProvisionedInfo(
    val apiEndpoint: String,
    val tenantUuid: String,
    val deviceUuid: String
)

data class EsperRuntimeInfo(
    val tenantConfig: EsperTenantConfig,
    val deviceId: String
)

data class EsperDeviceGroup(
    val id: String,
    val name: String?
)

data class EsperDeviceState(
    val groups: List<EsperDeviceGroup>,
    val currentBlueprintId: String?
)

data class MoveDeviceRequest(
    val deviceIds: List<String>
)
