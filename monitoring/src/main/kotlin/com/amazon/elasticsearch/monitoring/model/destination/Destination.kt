package com.amazon.elasticsearch.monitoring.model.destination

import com.amazon.elasticsearch.monitoring.util.DestinationType
import com.amazon.elasticsearch.util.convertToMap
import com.amazon.elasticsearch.util.instant
import com.amazon.elasticsearch.util.optionalTimeField
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import java.io.IOException
import java.time.Instant

/**
 * A value object that represents a Destination message.
 */
data class Destination(val id: String = NO_ID, val version: Long = NO_VERSION, val type: DestinationType,
                       val name: String, val lastUpdateTime: Instant,
                       val chime: Chime?, val slack: Slack?,
                       val customWebhook: CustomWebhook?): ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(DESTINATION_TYPE)
                .field(TYPE_FIELD, type.name)
                .field(NAME_FIELD, name)
                .optionalTimeField(LAST_UPDATE_TIME_FIELD, lastUpdateTime)
                .field(type.name, constructResponseForWebhookType(type))
                .endObject()
                .endObject()
    }

    companion object {
        const val DESTINATION_TYPE = "destination"
        const val TYPE_FIELD = "type"
        const val NAME_FIELD = "name"
        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val LAST_UPDATE_TIME_FIELD = "last_update_time"
        const val CHIME = "chime"
        const val SLACK = "slack"
        const val CUSTOMWEBHOOK = "custom_webhook"

        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): Destination {
            lateinit var name: String
            lateinit var type: String
            var slack: Slack? = null
            var chime: Chime? = null
            var customWebhook: CustomWebhook? = null
            var lastUpdateTime: Instant? = null

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    NAME_FIELD -> name = xcp.text()
                    TYPE_FIELD -> {
                        type = xcp.text()
                        val allowedTypes = mutableListOf<String>()
                        DestinationType.values().map { allowedTypes.add(it.type) }
                        if (!allowedTypes.contains(type)) {
                            throw IllegalStateException("Type should be one of the ${allowedTypes}")
                        }
                    }
                    LAST_UPDATE_TIME_FIELD -> lastUpdateTime = xcp.instant()
                    CHIME -> {
                        chime = Chime.parse(xcp)
                    }
                    SLACK -> {
                        slack = Slack.parse(xcp)
                    }
                    CUSTOMWEBHOOK -> {
                        customWebhook = CustomWebhook.parse(xcp)
                    }
                    else -> {
                        xcp.skipChildren()
                    }
                }
            }
            return Destination(id,
                    version,
                    DestinationType.valueOf(type.toUpperCase()),
                    requireNotNull(name) { "Destination name is null" },
                    lastUpdateTime ?: Instant.now(),
                    chime,
                    slack,
                    customWebhook)
        }
    }

    fun constructResponseForWebhookType(destination: DestinationType): Any? {
        var content: Any? = null
        when (destination) {
            DestinationType.CHIME -> content = chime?.convertToMap()?.get(destination.type)
            DestinationType.SLACK -> content = slack?.convertToMap()?.get(destination.type)
            DestinationType.CUSTOM_WEBHOOK -> content = customWebhook?.convertToMap()?.get(destination.type)
        }
        if (content == null) {
            throw IllegalArgumentException("Content is NULL for destination type ${destination.type}")
        }
        return content
    }
}
