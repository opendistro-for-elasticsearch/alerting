package com.amazon.opendistroforelasticsearch.alerting.settings

import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.settings.SecureSetting
import org.elasticsearch.common.settings.SecureString
import java.io.IOException

/**
 * settings specific to mail destination.
 */
data class DestinationMailSettings(
    val host: String,
    val port: Int,
    val method: String,
    val from: String,
    val username: SecureString,
    val password: SecureString
) {
    companion object {
        val DESTINATION_MAIL_HOST = Setting.simpleString(
                "opendistro.alerting.destination.mail.host",
                "localhost",
                Setting.Property.NodeScope
        )

        val DESTINATION_MAIL_PORT = Setting.intSetting(
                "opendistro.alerting.destination.mail.port",
                587,
                Setting.Property.NodeScope
        )

        val DESTINATION_MAIL_METHOD = Setting.simpleString(
                "opendistro.alerting.destination.mail.method",
                "none",
                Setting.Property.NodeScope
        )

        val DESTINATION_MAIL_FROM = Setting.simpleString(
                "opendistro.alerting.destination.mail.from",
                "opendistro-alerting@localhost",
                Setting.Property.NodeScope
        )

        val DESTINATION_MAIL_USERNAME = SecureSetting.secureString(
                "opendistro.alerting.destination.mail.username",
                null
        )

        val DESTINATION_MAIL_PASSWORD = SecureSetting.secureString(
                "opendistro.alerting.destination.mail.password",
                null
        )

        @JvmStatic
        @Throws(IOException::class)
        fun parse(settings: Settings): DestinationMailSettings {
            return DestinationMailSettings(
                DESTINATION_MAIL_HOST.get(settings),
                DESTINATION_MAIL_PORT.get(settings),
                DESTINATION_MAIL_METHOD.get(settings),
                DESTINATION_MAIL_FROM.get(settings),
                DESTINATION_MAIL_USERNAME.get(settings),
                DESTINATION_MAIL_PASSWORD.get(settings)
            )
        }
    }
}
