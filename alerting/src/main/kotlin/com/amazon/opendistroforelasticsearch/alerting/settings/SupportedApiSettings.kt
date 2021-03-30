package com.amazon.opendistroforelasticsearch.alerting.settings

import com.amazon.opendistroforelasticsearch.alerting.core.model.LocalUriInput

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

        /**
         * Set to TRUE to enable the supportedApiList check. Set to FALSE to disable.
         */
        // TODO HURNEYT: Currently set to TRUE for testing purposes.
        //  Should likely be set to FALSE by default.
        private var supportedApiListEnabled = true

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
         * If [supportedApiListEnabled] is TRUE, calls [validatePath] to confirm whether the provided path
         * is in supportedApiList. Will otherwise take no actions.
         * @param localUriInput The [LocalUriInput] to validate.
         * @return The path that was validated.
         */
        fun validateLocalUriInput(localUriInput: LocalUriInput): String {
            val path = localUriInput.toConstructedUri().path
            if (supportedApiListEnabled) validatePath(path)
            return path
        }

        /**
         * Confirms whether the provided path is in supportedApiList.
         * Throws an exception if the provided path is not on the list; otherwise performs no action.
         * @param path The path to validate.
         * @throws IllegalArgumentException When supportedApiList does not contain the provided path.
         */
        private fun validatePath(path: String) {
            if (!supportedApiList.contains(path)) throw IllegalArgumentException("API path not in supportedApiList: $path")
        }
    }
}
