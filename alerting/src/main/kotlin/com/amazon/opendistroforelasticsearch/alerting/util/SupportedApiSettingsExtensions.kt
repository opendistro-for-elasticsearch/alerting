package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.core.model.LocalUriInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.convertToMap
import com.amazon.opendistroforelasticsearch.alerting.settings.SupportedApiSettings
import com.amazon.opendistroforelasticsearch.alerting.settings.SupportedApiSettings.Companion.resolveToActionRequest
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequest
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.support.XContentMapValues

/**
 * Calls the appropriate transport action for the API requested in the [localUriInput].
 * @param localUriInput The [LocalUriInput] to resolve.
 * @param client The [Client] used to call the respective transport action.
 * @throws IllegalArgumentException When the requested API is not supported by this feature.
 */
fun executeTransportAction(localUriInput: LocalUriInput, client: Client): ActionResponse {
    return when (val request = resolveToActionRequest(localUriInput)) {
        is ClusterHealthRequest -> client.admin().cluster().health(request).get()
        is ClusterStatsRequest -> client.admin().cluster().clusterStats(request).get()
        else -> throw IllegalArgumentException("Unsupported API request: ${request.javaClass.name}")
    }
}

/**
 * Populates a [HashMap] with the values in the [ActionResponse].
 * @throws IllegalArgumentException when the [ActionResponse] is not supported by this feature.
 */
fun ActionResponse.toMap(): Map<String, Any> {
    return when (this) {
        is ClusterHealthResponse -> redactFieldsFromResponse(this.convertToMap(),
            SupportedApiSettings.getSupportedJsonPayload(SupportedApiSettings.CLUSTER_HEALTH_PATH))
        is ClusterStatsResponse -> redactFieldsFromResponse(this.convertToMap(),
            SupportedApiSettings.getSupportedJsonPayload(SupportedApiSettings.CLUSTER_STATS_PATH))
        else -> throw IllegalArgumentException("Unsupported ActionResponse type: ${this.javaClass.name}")
    }
}

/**
 * Populates a [HashMap] with only the values that support being exposed to users.
 */
@Suppress("UNCHECKED_CAST")
fun redactFieldsFromResponse(mappedActionResponse: Map<String, Any>, supportedJsonPayload: Map<String, ArrayList<String>>): Map<String, Any> {
    return when {
        supportedJsonPayload.isEmpty() -> mappedActionResponse
        else -> {
            val output = hashMapOf<String, Any>()
            for ((key, value) in supportedJsonPayload) {
                when (mappedActionResponse[key]) {
                    is Map<*, *> -> output[key] = XContentMapValues.filter(
                        mappedActionResponse[key] as MutableMap<String, *>?,
                        value.toTypedArray(), arrayOf()
                    )
                    else -> output[key] = value
                }
            }
            output
        }
    }
}
