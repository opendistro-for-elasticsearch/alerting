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

package com.amazon.opendistroforelasticsearch.alerting.model.destination.email

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.Locale

/**
 * A value object that represents an Email message. Email messages will be
 * submitted to the Email destination.
 */
data class Email(
    val emailAccountID: String,
    val recipients: List<Recipient>
) : ToXContent {

    init {
        require(recipients.isNotEmpty()) { "At least one recipient must be provided" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(TYPE)
                .field(EMAIL_ACCOUNT_ID_FIELD, emailAccountID)
                .field(RECIPIENTS_FIELD, recipients.toTypedArray())
                .endObject()
    }

    companion object {
        const val TYPE = "email"
        const val EMAIL_ACCOUNT_ID_FIELD = "email_account_id"
        const val RECIPIENTS_FIELD = "recipients"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Email {
            lateinit var emailAccountID: String
            val recipients: MutableList<Recipient> = mutableListOf()

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    EMAIL_ACCOUNT_ID_FIELD -> emailAccountID = xcp.text()
                    RECIPIENTS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            recipients.add(Recipient.parse(xcp))
                        }
                    }
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing email destination")
                    }
                }
            }

            return Email(
                    requireNotNull(emailAccountID) { "Email account ID is null" },
                    recipients
            )
        }
    }
}

/**
 * A value object containing a recipient for an Email.
 */
data class Recipient(
    val type: RecipientType,
    val emailGroupId: String?,
    val email: String?
) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject().field(TYPE_FIELD, type.value)

        when (type) {
            RecipientType.EMAIL_GROUP -> {
                if (emailGroupId == null) {
                    throw IllegalArgumentException("Email group ID is null for recipient type ${type.value}")
                }
                builder.field(EMAIL_GROUP_ID_FIELD, emailGroupId)
            }
            RecipientType.EMAIL -> {
                if (email == null) {
                    throw IllegalArgumentException("Email is null for recipient type ${type.value}")
                }
                builder.field(EMAIL_FIELD, email)
            }
        }

        return builder.endObject()
    }

    enum class RecipientType(val value: String) {
        EMAIL("email"),
        EMAIL_GROUP("email_group")
    }

    companion object {
        const val TYPE_FIELD = "type"
        const val EMAIL_GROUP_ID_FIELD = "email_group_id"
        const val EMAIL_FIELD = "email"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Recipient {
            lateinit var type: String
            lateinit var emailGroupID: String
            lateinit var email: String

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    TYPE_FIELD -> {
                        type = xcp.text()
                        val allowedTypes = RecipientType.values().map { it.value }
                        if (!allowedTypes.contains(type)) {
                            throw IllegalStateException("Type should be one of $allowedTypes")
                        }
                    }
                    EMAIL_GROUP_ID_FIELD -> emailGroupID = requireNotNull(xcp.text()) { "Email group ID is null" }
                    EMAIL_FIELD -> email = requireNotNull(xcp.text()) { "Email is null" }
                }
            }

            return Recipient(
                    RecipientType.valueOf(type.toUpperCase(Locale.ROOT)),
                    emailGroupID,
                    email
            )
        }
    }
}
