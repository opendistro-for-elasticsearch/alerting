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
import com.amazon.opendistroforelasticsearch.alerting.destination.message.EmailMessage
import com.amazon.opendistroforelasticsearch.alerting.destination.message.SlackMessage
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationResponse
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.convertToMap
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.instant
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalTimeField
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalUserField
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.Email
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils.Companion.NO_SCHEMA_VERSION
import com.amazon.opendistroforelasticsearch.alerting.util.isHostInDenylist
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.apache.logging.log4j.LogManager
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.net.InetAddress
import java.time.Instant
import java.util.Locale

/**
 * A value object that represents a Destination message.
 */
data class Destination(
    val id: String = NO_ID,
    val version: Long = NO_VERSION,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val seqNo: Int = NO_SEQ_NO,
    val primaryTerm: Int = NO_PRIMARY_TERM,
    val type: DestinationType,
    val name: String,
    val user: User?,
    val lastUpdateTime: Instant,
    val chime: Chime?,
    val slack: Slack?,
    val customWebhook: CustomWebhook?,
    val email: Email?
) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        if (params.paramAsBoolean("with_type", false)) builder.startObject(DESTINATION)
        builder.field(ID_FIELD, id)
                .field(TYPE_FIELD, type.value)
                .field(NAME_FIELD, name)
                .optionalUserField(USER_FIELD, user)
                .field(SCHEMA_VERSION, schemaVersion)
                .field(SEQ_NO_FIELD, seqNo)
                .field(PRIMARY_TERM_FIELD, primaryTerm)
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
        out.writeInt(seqNo)
        out.writeInt(primaryTerm)
        out.writeEnum(type)
        out.writeString(name)
        out.writeBoolean(user != null)
        user?.writeTo(out)
        out.writeInstant(lastUpdateTime)
        out.writeBoolean(chime != null)
        chime?.writeTo(out)
        out.writeBoolean(slack != null)
        slack?.writeTo(out)
        out.writeBoolean(customWebhook != null)
        customWebhook?.writeTo(out)
        out.writeBoolean(email != null)
        email?.writeTo(out)
    }

    companion object {
        const val DESTINATION = "destination"
        const val ID_FIELD = "id"
        const val TYPE_FIELD = "type"
        const val NAME_FIELD = "name"
        const val USER_FIELD = "user"
        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val NO_SEQ_NO = 0
        const val NO_PRIMARY_TERM = 0
        const val SCHEMA_VERSION = "schema_version"
        const val SEQ_NO_FIELD = "seq_no"
        const val PRIMARY_TERM_FIELD = "primary_term"
        const val LAST_UPDATE_TIME_FIELD = "last_update_time"
        const val CHIME = "chime"
        const val SLACK = "slack"
        const val CUSTOMWEBHOOK = "custom_webhook"
        const val EMAIL = "email"

        // This constant is used for test actions created part of integ tests
        const val TEST_ACTION = "test"

        private val logger = LogManager.getLogger(Destination::class.java)

        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun parse(
            xcp: XContentParser,
            id: String = NO_ID,
            version: Long = NO_VERSION,
            seqNo: Int = NO_SEQ_NO,
            primaryTerm: Int = NO_PRIMARY_TERM
        ): Destination {

            lateinit var name: String
            var user: User? = null
            lateinit var type: String
            var slack: Slack? = null
            var chime: Chime? = null
            var customWebhook: CustomWebhook? = null
            var email: Email? = null
            var lastUpdateTime: Instant? = null
            var schemaVersion = NO_SCHEMA_VERSION

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    NAME_FIELD -> name = xcp.text()
                    USER_FIELD -> user = if (xcp.currentToken() == XContentParser.Token.VALUE_NULL) null else User.parse(xcp)
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
                    EMAIL -> {
                        email = Email.parse(xcp)
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
                    seqNo,
                    primaryTerm,
                    DestinationType.valueOf(type.toUpperCase(Locale.ROOT)),
                    requireNotNull(name) { "Destination name is null" },
                    user,
                    lastUpdateTime ?: Instant.now(),
                    chime,
                    slack,
                    customWebhook,
                    email)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun parseWithType(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): Destination {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp)
            ensureExpectedToken(XContentParser.Token.FIELD_NAME, xcp.nextToken(), xcp)
            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp)
            val destination = parse(xcp, id, version)
            ensureExpectedToken(XContentParser.Token.END_OBJECT, xcp.nextToken(), xcp)
            return destination
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): Destination {
            return Destination(
                id = sin.readString(),
                version = sin.readLong(),
                schemaVersion = sin.readInt(),
                seqNo = sin.readInt(),
                primaryTerm = sin.readInt(),
                type = sin.readEnum(DestinationType::class.java),
                name = sin.readString(),
                user = if (sin.readBoolean()) {
                    User(sin)
                } else null,
                lastUpdateTime = sin.readInstant(),
                chime = Chime.readFrom(sin),
                slack = Slack.readFrom(sin),
                customWebhook = CustomWebhook.readFrom(sin),
                email = Email.readFrom(sin)
            )
        }
    }

    @Throws(IOException::class)
    fun publish(
        compiledSubject: String?,
        compiledMessage: String,
        destinationCtx: DestinationContext,
        denyHostRanges: List<String>
    ): String {

        val destinationMessage: BaseMessage
        val responseContent: String
        val responseStatusCode: Int
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
                        .withMethod(customWebhook?.method)
                        .withQueryParams(customWebhook?.queryParams)
                        .withHeaderParams(customWebhook?.headerParams)
                        .withMessage(compiledMessage).build()
            }
            DestinationType.EMAIL -> {
                val emailAccount = destinationCtx.emailAccount
                destinationMessage = EmailMessage.Builder(name)
                        .withHost(emailAccount?.host)
                        .withPort(emailAccount?.port)
                        .withMethod(emailAccount?.method?.value)
                        .withUserName(emailAccount?.username)
                        .withPassword(emailAccount?.password)
                        .withFrom(emailAccount?.email)
                        .withRecipients(destinationCtx.recipients)
                        .withSubject(compiledSubject)
                        .withMessage(compiledMessage).build()
            }
            DestinationType.TEST_ACTION -> {
                return "test action"
            }
        }

        validateDestinationUri(destinationMessage, denyHostRanges)
        val response = Notification.publish(destinationMessage) as DestinationResponse
        responseContent = response.responseContent
        responseStatusCode = response.statusCode

        logger.info("Message published for action name: $name, messageid: $responseContent, statuscode: $responseStatusCode")
        return responseContent
    }

    fun constructResponseForDestinationType(type: DestinationType): Any {
        var content: Any? = null
        when (type) {
            DestinationType.CHIME -> content = chime?.convertToMap()?.get(type.value)
            DestinationType.SLACK -> content = slack?.convertToMap()?.get(type.value)
            DestinationType.CUSTOM_WEBHOOK -> content = customWebhook?.convertToMap()?.get(type.value)
            DestinationType.EMAIL -> content = email?.convertToMap()?.get(type.value)
            DestinationType.TEST_ACTION -> content = "dummy"
        }
        if (content == null) {
            throw IllegalArgumentException("Content is NULL for destination type ${type.value}")
        }
        return content
    }

    private fun validateDestinationUri(destinationMessage: BaseMessage, denyHostRanges: List<String>) {
        if (destinationMessage.isHostInDenylist(denyHostRanges)) {
            logger.error("Host: {} resolves to: {} which is in denylist: {}.", destinationMessage.uri.host,
                    InetAddress.getByName(destinationMessage.uri.host), denyHostRanges)
            throw IOException("The destination address is invalid.")
        }
    }
}
