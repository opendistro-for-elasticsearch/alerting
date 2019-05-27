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

import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.lang.IllegalStateException

/**
 * A value object that represents a Mail message. Mail message will be
 * submitted to the Mail destination
 */
data class Mail(
    val host: String?,
    val port: Int?,
    val auth: Boolean?,
    val method: String?,
    val from: String?,
    val recipients: String?,
    val subject: String?,
    val username: String?,
    val password: String?
) : ToXContent {

    init {
        require(!Strings.isNullOrEmpty(host)) {
            "Host name must be provided."
        }
        require(!Strings.isNullOrEmpty(from)) {
            "From address must be provided."
        }
        require(!Strings.isNullOrEmpty(recipients)) {
            "Comma separated recipients must be provided."
        }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(TYPE)
                .field(HOST_FIELD, host)
                .field(PORT_FIELD, port)
                .field(AUTH_FIELD, auth)
                .field(METHOD_FIELD, method)
                .field(FROM_FIELD, from)
                .field(RECIPIENTS_FIELD, recipients)
                .field(SUBJECT_FIELD, subject)
                .field(USERNAME_FIELD, username)
                .field(PASSWORD_FIELD, password)
                .endObject()
    }

    companion object {
        const val TYPE = "mail"
        const val HOST_FIELD = "host"
        const val PORT_FIELD = "port"
        const val AUTH_FIELD = "auth"
        const val METHOD_FIELD = "method"
        const val FROM_FIELD = "from"
        const val RECIPIENTS_FIELD = "recipients"
        const val SUBJECT_FIELD = "subject"
        const val USERNAME_FIELD = "username"
        const val PASSWORD_FIELD = "password"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Mail {
            var host: String? = null
            var port: Int? = 25
            var auth: Boolean? = false
            var method: String? = "plain"
            var from: String? = null
            var recipients: String? = null
            var subject: String? = null
            var username: String? = null
            var password: String? = null

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    HOST_FIELD -> host = xcp.textOrNull()
                    PORT_FIELD -> port = xcp.intValue()
                    AUTH_FIELD -> auth = xcp.booleanValue()
                    METHOD_FIELD -> method = xcp.textOrNull()
                    FROM_FIELD -> from = xcp.textOrNull()
                    RECIPIENTS_FIELD -> recipients = xcp.textOrNull()
                    SUBJECT_FIELD -> subject = xcp.textOrNull()
                    USERNAME_FIELD -> username = xcp.textOrNull()
                    PASSWORD_FIELD -> password = xcp.textOrNull()
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing mail destination")
                    }
                }
            }
            return Mail(host, port, auth, method, from, recipients, subject, username, password)
        }
    }
}
