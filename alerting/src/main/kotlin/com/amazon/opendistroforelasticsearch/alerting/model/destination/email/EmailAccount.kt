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
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.settings.SecureString
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * A value object that represents an Email Account. Email Accounts contain the configuration
 * information for sender emails when sending email messages through the Email destination.
 */
data class EmailAccount(
    val id: String = NO_ID,
    val version: Long = NO_VERSION,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val name: String,
    val email: String,
    val host: String,
    val port: Int,
    val method: MethodType,
    val username: SecureString? = null,
    val password: SecureString? = null
) : Writeable, ToXContent {

    init {
        // Excluding dashes (-) from valid names for EmailAccount since the name is used
        // to namespace the associated Elasticsearch keystore settings and dashes do not work for those settings.
        val validNamePattern = Regex("[A-Z0-9_]+", RegexOption.IGNORE_CASE)
        require(validNamePattern.matches(name)) {
            "Invalid email account name. Valid characters are upper and lowercase a-z, 0-9, and _ (underscore)."
        }

        require(isValidEmail(email)) { "Invalid email" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        if (params.paramAsBoolean("with_type", false)) builder.startObject(EMAIL_ACCOUNT_TYPE)
        builder.field(SCHEMA_VERSION, schemaVersion)
                .field(NAME_FIELD, name)
                .field(EMAIL_FIELD, email)
                .field(HOST_FIELD, host)
                .field(PORT_FIELD, port)
                .field(METHOD_FIELD, method.value)
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
        out.writeString(email)
        out.writeString(host)
        out.writeInt(port)
        out.writeEnum(method)
        out.writeOptionalSecureString(username)
        out.writeOptionalSecureString(password)
    }

    enum class MethodType(val value: String) {
        NONE("none"),
        SSL("ssl"),
        TLS("starttls");

        companion object {
            private val values = values()
            // Created this method since MethodType value does not necessarily match enum name
            fun getByValue(value: String) = values.firstOrNull { it.value == value }
        }
    }

    companion object {
        const val EMAIL_ACCOUNT_TYPE = "email_account"
        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val SCHEMA_VERSION = "schema_version"
        const val NAME_FIELD = "name"
        const val EMAIL_FIELD = "email"
        const val HOST_FIELD = "host"
        const val PORT_FIELD = "port"
        const val METHOD_FIELD = "method"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): EmailAccount {
            var schemaVersion = NO_SCHEMA_VERSION
            lateinit var name: String
            lateinit var email: String
            lateinit var host: String
            var port: Int = -1
            lateinit var method: String

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    SCHEMA_VERSION -> schemaVersion = xcp.intValue()
                    NAME_FIELD -> name = xcp.text()
                    EMAIL_FIELD -> email = xcp.text()
                    HOST_FIELD -> host = xcp.text()
                    PORT_FIELD -> port = xcp.intValue()
                    METHOD_FIELD -> {
                        method = xcp.text()
                        val allowedMethods = MethodType.values().map { it.value }
                        if (!allowedMethods.contains(method)) {
                            throw IllegalStateException("Method should be one of $allowedMethods")
                        }
                    }
                }
            }

            return EmailAccount(id,
                    version,
                    schemaVersion,
                    name,
                    email,
                    host,
                    port,
                    requireNotNull(MethodType.getByValue(method)) { "Method type was null" }
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun parseWithType(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): EmailAccount {
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            val emailAccount = parse(xcp, id, version)
            ensureExpectedToken(Token.END_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            return emailAccount
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): EmailAccount {
            return EmailAccount(
                sin.readString(), // id
                sin.readLong(), // version
                sin.readInt(), // schemaVersion
                sin.readString(), // name
                sin.readString(), // email
                sin.readString(), // host
                sin.readInt(), // port
                sin.readEnum(MethodType::class.java), // method
                sin.readOptionalSecureString(), // username
                sin.readOptionalSecureString() // password
            )
        }
    }
}
