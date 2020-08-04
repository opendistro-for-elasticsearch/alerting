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

import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils.Companion.NO_SCHEMA_VERSION
import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * A value object that represents a group of recipient emails to send emails to.
 */
data class EmailGroup(
    val id: String = NO_ID,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val name: String,
    val emails: List<EmailEntry>
) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        if (params.paramAsBoolean("with_type", false)) builder.startObject(EMAIL_GROUP_TYPE)
        builder.field(SCHEMA_VERSION, schemaVersion)
                .field(NAME_FIELD, name)
                .field(EMAILS_FIELD, emails.toTypedArray())
        if (params.paramAsBoolean("with_type", false)) builder.endObject()
        return builder.endObject()
    }

    fun toXContent(builder: XContentBuilder): XContentBuilder {
        return toXContent(builder, ToXContent.EMPTY_PARAMS)
    }

    fun getEmailsAsListOfString(): List<String> {
        val emailsAsListOfString: MutableList<String> = mutableListOf()
        emails.map { emailsAsListOfString.add(it.email) }
        return emailsAsListOfString
    }

    companion object {
        const val EMAIL_GROUP_TYPE = "email_group"
        const val NO_ID = ""
        const val SCHEMA_VERSION = "schema_version"
        const val NAME_FIELD = "name"
        const val EMAILS_FIELD = "emails"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID): EmailGroup {
            var schemaVersion = NO_SCHEMA_VERSION
            lateinit var name: String
            val emails: MutableList<EmailEntry> = mutableListOf()

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    SCHEMA_VERSION -> schemaVersion = xcp.intValue()
                    NAME_FIELD -> name = xcp.text()
                    EMAILS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            emails.add(EmailEntry.parse(xcp))
                        }
                    }
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing email group")
                    }
                }
            }

            return EmailGroup(id,
                    schemaVersion,
                    requireNotNull(name) { "Email group name is null" },
                    emails
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun parseWithType(xcp: XContentParser, id: String = NO_ID): EmailGroup {
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            val emailGroup = parse(xcp, id)
            ensureExpectedToken(Token.END_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            return emailGroup
        }
    }
}

data class EmailEntry(val email: String) : ToXContent {

    init {
        require(!Strings.isEmpty(email)) { "Email entry must have a non-empty email" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field(EMAIL_FIELD, email)
                .endObject()
    }

    companion object {
        const val EMAIL_FIELD = "email"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): EmailEntry {
            lateinit var email: String

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    EMAIL_FIELD -> email = xcp.text()
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing email entry")
                    }
                }
            }

            return EmailEntry(email)
        }
    }
}
