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
import com.amazon.opendistroforelasticsearch.alerting.util.isValidEmail
import org.elasticsearch.common.Strings
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
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
    val version: Long = NO_VERSION,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val name: String,
    val emails: List<EmailEntry>
) : Writeable, ToXContent {

    init {
        val validNamePattern = Regex("[A-Z0-9_-]+", RegexOption.IGNORE_CASE)
        require(validNamePattern.matches(name)) {
            "Invalid email group name. Valid characters are upper and lowercase a-z, 0-9, _ (underscore) and - (hyphen)."
        }
    }

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

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeLong(version)
        out.writeInt(schemaVersion)
        out.writeString(name)
        out.writeCollection(emails)
    }

    fun getEmailsAsListOfString(): List<String> {
        val emailsAsListOfString: MutableList<String> = mutableListOf()
        emails.map { emailsAsListOfString.add(it.email) }
        return emailsAsListOfString
    }

    companion object {
        const val EMAIL_GROUP_TYPE = "email_group"
        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val SCHEMA_VERSION = "schema_version"
        const val NAME_FIELD = "name"
        const val EMAILS_FIELD = "emails"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): EmailGroup {
            var schemaVersion = NO_SCHEMA_VERSION
            lateinit var name: String
            val emails: MutableList<EmailEntry> = mutableListOf()

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    SCHEMA_VERSION -> schemaVersion = xcp.intValue()
                    NAME_FIELD -> name = xcp.text()
                    EMAILS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp)
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
                    version,
                    schemaVersion,
                    requireNotNull(name) { "Email group name is null" },
                    emails
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun parseWithType(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): EmailGroup {
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp)
            ensureExpectedToken(Token.FIELD_NAME, xcp.nextToken(), xcp)
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp)
            val emailGroup = parse(xcp, id, version)
            ensureExpectedToken(Token.END_OBJECT, xcp.nextToken(), xcp)
            return emailGroup
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): EmailGroup {
            return EmailGroup(
                sin.readString(), // id
                sin.readLong(), // version
                sin.readInt(), // schemaVersion
                sin.readString(), // name
                sin.readList(::EmailEntry) // emails
            )
        }
    }
}

data class EmailEntry(val email: String) : Writeable, ToXContent {

    init {
        require(!Strings.isEmpty(email)) { "Email entry must have a non-empty email" }
        require(isValidEmail(email)) { "Invalid email" }
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readString() // email
    )

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field(EMAIL_FIELD, email)
                .endObject()
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(email)
    }

    companion object {
        const val EMAIL_FIELD = "email"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): EmailEntry {
            lateinit var email: String

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp)
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

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): EmailEntry {
            return EmailEntry(sin)
        }
    }
}
