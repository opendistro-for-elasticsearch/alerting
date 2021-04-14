package com.amazon.opendistroforelasticsearch.alerting.settings

import com.amazon.opendistroforelasticsearch.alerting.core.model.LocalUriInput
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequest

/**
 * A class that supports storing a unique set of API paths that can be accessed by general users.
 */
class SupportedApiSettings {
    companion object {
        const val CLUSTER_HEALTH_PATH = "/_cluster/health"
        const val CLUSTER_STATS_PATH = "/_cluster/stats"

        /**
         * Each String represents the path to call an API.
         * NOTE: Paths should conform to the following pattern:
         * "/_cluster/health"
         *
         * Each Set<String> represents the supported JSON payload for the respective API.
         */
        private var supportedApiList = HashMap<String, Map<String, Set<String>>>()

        init {
            supportedApiList[CLUSTER_HEALTH_PATH] = hashMapOf()
            supportedApiList[CLUSTER_STATS_PATH] = hashMapOf()
        }

        /**
         * Returns the map of all supported json payload associated with the provided path from supportedApiList.
         * @param path The path for the requested API.
         * @return The map of all supported json payload for the requested API.
         * @throws IllegalArgumentException When supportedApiList does not contain a value for the provided key.
         */
        fun getSupportedJsonPayload(path: String): Map<String, Set<String>> {
            return supportedApiList[path] ?: throw IllegalArgumentException("API path not in supportedApiList: $path")
        }

        /**
         * Calls [validatePath] to confirm whether the provided path is in supportedApiList.
         * Will otherwise throw an exception.
         * @param localUriInput The [LocalUriInput] to validate.
         * @throws IllegalArgumentException When the requested API is not supported.
         * @return The path that was validated.
         */
        fun resolveToActionRequest(localUriInput: LocalUriInput): ActionRequest {
            val path = localUriInput.toConstructedUri().path
            validatePath(path)
            return when (path) {
                CLUSTER_HEALTH_PATH -> ClusterHealthRequest()
                CLUSTER_STATS_PATH -> ClusterStatsRequest()
                else -> throw IllegalArgumentException("Unsupported API: $path")
            }
        }

        /**
         * Confirms whether the provided path is in supportedApiList.
         * Throws an exception if the provided path is not on the list; otherwise performs no action.
         * @param path The path to validate.
         * @throws IllegalArgumentException When supportedApiList does not contain the provided path.
         */
        fun validatePath(path: String) {
            if (!supportedApiList.contains(path)) throw IllegalArgumentException("API path not in supportedApiList: $path")
        }
    }
}
