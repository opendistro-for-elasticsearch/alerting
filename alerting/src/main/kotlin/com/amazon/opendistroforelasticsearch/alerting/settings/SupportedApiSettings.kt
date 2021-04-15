package com.amazon.opendistroforelasticsearch.alerting.settings

import com.amazon.opendistroforelasticsearch.alerting.core.model.LocalUriInput
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequest
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.json.JsonXContent

/**
 * A class that supports storing a unique set of API paths that can be accessed by general users.
 */
class SupportedApiSettings {
    companion object {
        const val CLUSTER_HEALTH_PATH = "/_cluster/health"
        const val CLUSTER_STATS_PATH = "/_cluster/stats"

        private const val RESOURCE_FILE = "supported_json_payloads.json"

        /**
         * The key in this map represents the path to call an API.
         * NOTE: Paths should conform to the following pattern:
         * "/_cluster/stats"
         *
         * The value in these maps represents a path root mapped to a list of paths to field values.
         * NOTE: Keys in this map should consist of root components of the response body; e.g.,:
         * "indices"
         *
         * Values in these maps should consist of the remaining fields in the path
         * to the supported value separated by periods; e.g.,:
         * "shards.total",
         * "shards.index.shards.min"
         *
         * In this example for ClusterStats, the response will only include
         * the values at the end of these two paths:
         * "/_cluster/stats": {
         *      "indices": [
         *          "shards.total",
         *          "shards.index.shards.min"
         *      ]
         * }
         */
        private var supportedApiList = HashMap<String, Map<String, ArrayList<String>>>()

        init {
            val supportedJsonPayloads = SupportedApiSettings::class.java.getResource(RESOURCE_FILE)
            @Suppress("UNCHECKED_CAST")
            if (supportedJsonPayloads != null) supportedApiList =
                XContentHelper.convertToMap(
                    JsonXContent.jsonXContent, supportedJsonPayloads.readText(), false) as HashMap<String, Map<String, ArrayList<String>>>
        }

        /**
         * Returns the map of all supported json payload associated with the provided path from supportedApiList.
         * @param path The path for the requested API.
         * @return The map of all supported json payload for the requested API.
         * @throws IllegalArgumentException When supportedApiList does not contain a value for the provided key.
         */
        fun getSupportedJsonPayload(path: String): Map<String, ArrayList<String>> {
            return supportedApiList[path] ?: throw IllegalArgumentException("API path not in supportedApiList: $path")
        }

        /**
         * Calls [validatePath] to confirm whether the provided [LocalUriInput]'s path is in [supportedApiList].
         * Will then return an [ActionRequest] for the API associated with that path.
         * Will otherwise throw an exception.
         * @param localUriInput The [LocalUriInput] to resolve.
         * @throws IllegalArgumentException when the requested API is not supported.
         * @return The [ActionRequest] for the API associated with the provided [LocalUriInput].
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
         * Confirms whether the provided path is in [supportedApiList].
         * Throws an exception if the provided path is not on the list; otherwise performs no action.
         * @param path The path to validate.
         * @throws IllegalArgumentException when supportedApiList does not contain the provided path.
         */
        fun validatePath(path: String) {
            if (!supportedApiList.contains(path)) throw IllegalArgumentException("API path not in supportedApiList: $path")
        }
    }
}
