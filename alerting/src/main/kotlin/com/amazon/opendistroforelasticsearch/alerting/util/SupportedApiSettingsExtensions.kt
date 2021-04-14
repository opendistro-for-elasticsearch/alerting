package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.core.model.LocalUriInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.convertToMap
import com.amazon.opendistroforelasticsearch.alerting.settings.SupportedApiSettings.Companion.resolveToActionRequest
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequest
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse
import org.elasticsearch.client.Client

fun executeTransportAction(localUriInput: LocalUriInput, client: Client): ActionResponse {
    return when (val request = resolveToActionRequest(localUriInput)) {
        is ClusterHealthRequest -> client.admin().cluster().health(request).get()
        is ClusterStatsRequest -> client.admin().cluster().clusterStats(request).get()
        else -> throw IllegalArgumentException("Unsupported API request: ${request.javaClass.name}")
    }
}

fun ActionResponse.toMap(): Map<String, Any> {
    return when (this) {
        is ClusterHealthResponse -> this.convertToMap()
        is ClusterStatsResponse -> this.convertToMap()
        else -> throw IllegalArgumentException("Unsupported ActionResponse type: ${this.javaClass.name}")
    }
}
