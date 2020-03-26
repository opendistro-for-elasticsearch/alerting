package com.amazon.opendistroforelasticsearch.alerting.settings

import org.elasticsearch.common.settings.Settings
import java.io.IOException

/**
 * settings specific to mail destination.
 */
data class DestinationSettings(
    val mail: DestinationMailSettings
) {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun parse(settings: Settings): DestinationSettings {
            val prefix: (String) -> (Boolean) = { it.startsWith("opendistro.alerting.destination.mail") }
            return DestinationSettings(
                DestinationMailSettings.parse(settings.filter(prefix))
            )
        }
    }
}
