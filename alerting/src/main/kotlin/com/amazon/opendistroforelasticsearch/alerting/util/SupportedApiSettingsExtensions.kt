package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.core.model.LocalUriInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.convertToMap
import com.amazon.opendistroforelasticsearch.alerting.settings.SupportedApiSettings
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequest
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse
import org.elasticsearch.client.Client

fun executeTransportAction(localUriInput: LocalUriInput, client: Client): ActionResponse {
    val path = SupportedApiSettings.validateLocalUriInput(localUriInput)
    if (path == SupportedApiSettings.CLUSTER_HEALTH_PATH) {
        return client.admin().cluster().health(ClusterHealthRequest()).get()
    }
    if (path == SupportedApiSettings.CLUSTER_STATS_PATH) {
        return client.admin().cluster().clusterStats(ClusterStatsRequest()).get()
    }
    throw IllegalArgumentException("Unsupported API: $path")
}

fun ActionResponse.toMap(): Map<String, Any> {
    if (this is ClusterHealthResponse) {
        return this.convertToMap()
    }
    if (this is ClusterStatsResponse) {
        return this.convertToMap()
    }
    throw IllegalArgumentException("Unsupported ActionResponse type: ${this.javaClass.name}")
}
