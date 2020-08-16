/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.alerting.model.destination

import com.amazon.opendistroforelasticsearch.alerting.destination.Notification
import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage
import com.amazon.opendistroforelasticsearch.alerting.destination.message.ChimeMessage
import com.amazon.opendistroforelasticsearch.alerting.destination.message.CustomWebhookMessage
import com.amazon.opendistroforelasticsearch.alerting.destination.message.SlackMessage
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationHttpResponse
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.convertToMap
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.instant
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalTimeField
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils.Companion.NO_SCHEMA_VERSION
import org.apache.logging.log4j.LogManager
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import java.io.IOException
import java.time.Instant
import java.util.Locale

/**
 * A value object that represents a Destination message.
 */
data class Destination(
    val id: String = NO_ID,
    val version: Long = NO_VERSION,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val type: DestinationType,
    val name: String,
    val lastUpdateTime: Instant,
    val chime: Chime?,
    val slack: Slack?,
    val customWebhook: CustomWebhook?
) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        if (params.paramAsBoolean("with_type", false)) builder.startObject(DESTINATION)
        builder.field(TYPE_FIELD, type.value)
                .field(NAME_FIELD, name)
                .field(SCHEMA_VERSION, schemaVersion)
                .optionalTimeField(LAST_UPDATE_TIME_FIELD, lastUpdateTime)
                .field(type.value, constructResponseForDestinationType(type))
        if (params.paramAsBoolean("with_type", false)) builder.endObject()
        return builder.endObject()
    }

    fun toXContent(builder: XContentBuilder): XContentBuilder {
        return toXContent(builder, ToXContent.EMPTY_PARAMS)
    }

    @Throws(IOException::class)
    fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeLong(version)
        out.writeInt(schemaVersion)
        out.writeEnum(type)
        out.writeString(name)
        out.writeInstant(lastUpdateTime)
        if (chime != null) {
            out.writeBoolean(true)
            chime.writeTo(out)
        } else {
            out.writeBoolean(false)
        }
        if (slack != null) {
            out.writeBoolean(true)
            slack.writeTo(out)
        } else {
            out.writeBoolean(false)
        }
        if (customWebhook != null) {
            out.writeBoolean(true)
            customWebhook.writeTo(out)
        } else {
            out.writeBoolean(false)
        }
    }

    companion object {
        const val DESTINATION = "destination"
        const val TYPE_FIELD = "type"
        const val NAME_FIELD = "name"
        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val SCHEMA_VERSION = "schema_version"
        const val LAST_UPDATE_TIME_FIELD = "last_update_time"
        const val CHIME = "chime"
        const val SLACK = "slack"
        const val CUSTOMWEBHOOK = "custom_webhook"
        // This constant is used for test actions created part of integ tests
        const val TEST_ACTION = "test"

        private val logger = LogManager.getLogger(Destination::class.java)

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
            var schemaVersion = NO_SCHEMA_VERSION

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    NAME_FIELD -> name = xcp.text()
                    TYPE_FIELD -> {
                        type = xcp.text()
                        val allowedTypes = DestinationType.values().map { it.value }
                        if (!allowedTypes.contains(type)) {
                            throw IllegalStateException("Type should be one of the $allowedTypes")
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
                    TEST_ACTION -> {
                        // This condition is for integ tests to avoid parsing
                    }
                    SCHEMA_VERSION -> {
                        schemaVersion = xcp.intValue()
                    }
                    else -> {
                        xcp.skipChildren()
                    }
                }
            }
            return Destination(id,
                    version,
                    schemaVersion,
                    DestinationType.valueOf(type.toUpperCase(Locale.ROOT)),
                    requireNotNull(name) { "Destination name is null" },
                    lastUpdateTime ?: Instant.now(),
                    chime,
                    slack,
                    customWebhook)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): Destination {
            return Destination(
                sin.readString(), // id
                sin.readLong(), // version
                sin.readInt(), // schemaVersion
                sin.readEnum(DestinationType::class.java), // type
                sin.readString(), // name
                sin.readInstant(), // lastUpdateTime
                Chime.readFrom(sin), // chime
                Slack.readFrom(sin), // slack
                CustomWebhook.readFrom(sin) // customWebhook
            )
        }
    }

    @Throws(IOException::class)
    fun publish(compiledSubject: String?, compiledMessage: String): String {
        val destinationMessage: BaseMessage
        when (type) {
            DestinationType.CHIME -> {
                val messageContent = chime?.constructMessageContent(compiledSubject, compiledMessage)
                destinationMessage = ChimeMessage.Builder(name)
                        .withUrl(chime?.url)
                        .withMessage(messageContent)
                        .build()
            }
            DestinationType.SLACK -> {
                val messageContent = slack?.constructMessageContent(compiledSubject, compiledMessage)
                destinationMessage = SlackMessage.Builder(name)
                        .withUrl(slack?.url)
                        .withMessage(messageContent)
                        .build()
            }
            DestinationType.CUSTOM_WEBHOOK -> {
                destinationMessage = CustomWebhookMessage.Builder(name)
                        .withUrl(customWebhook?.url)
                        .withScheme(customWebhook?.scheme)
                        .withHost(customWebhook?.host)
                        .withPort(customWebhook?.port)
                        .withPath(customWebhook?.path)
                        .withQueryParams(customWebhook?.queryParams)
                        .withHeaderParams(customWebhook?.headerParams)
                        .withMessage(compiledMessage).build()
            }
            DestinationType.TEST_ACTION -> {
                return "test action"
            }
        }
        val response = Notification.publish(destinationMessage) as DestinationHttpResponse
        logger.info("Message published for action name: $name, messageid: ${response.responseContent}, statuscode: ${response.statusCode}")
        return response.responseContent
    }

    fun constructResponseForDestinationType(type: DestinationType): Any {
        var content: Any? = null
        when (type) {
            DestinationType.CHIME -> content = chime?.convertToMap()?.get(type.value)
            DestinationType.SLACK -> content = slack?.convertToMap()?.get(type.value)
            DestinationType.CUSTOM_WEBHOOK -> content = customWebhook?.convertToMap()?.get(type.value)
            DestinationType.TEST_ACTION -> content = "dummy"
        }
        if (content == null) {
            throw IllegalArgumentException("Content is NULL for destination type ${type.value}")
        }
        return content
    }
}
