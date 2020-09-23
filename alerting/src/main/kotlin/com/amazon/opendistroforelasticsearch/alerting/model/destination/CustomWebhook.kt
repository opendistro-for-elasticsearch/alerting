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
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.lang.IllegalStateException

/**
 * A value object that represents a Custom webhook message. Webhook message will be
 * submitted to the Custom webhook destination
 */
data class CustomWebhook(
    val url: String?,
    val scheme: String?,
    val host: String?,
    val port: Int,
    val path: String?,
    val queryParams: Map<String, String>,
    val headerParams: Map<String, String>,
    val username: String?,
    val password: String?
) : ToXContent {

    init {
        require(!(Strings.isNullOrEmpty(url) && Strings.isNullOrEmpty(host))) {
            "Url or Host name must be provided."
        }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(TYPE)
                .field(URL, url)
                .field(SCHEME_FIELD, scheme)
                .field(HOST_FIELD, host)
                .field(PORT_FIELD, port)
                .field(PATH_FIELD, path)
                .field(QUERY_PARAMS_FIELD, queryParams)
                .field(HEADER_PARAMS_FIELD, headerParams)
                .field(USERNAME_FIELD, username)
                .field(PASSWORD_FIELD, password)
                .endObject()
    }

    @Throws(IOException::class)
    fun writeTo(out: StreamOutput) {
        out.writeString(url)
        out.writeOptionalString(scheme)
        out.writeString(host)
        out.writeOptionalInt(port)
        out.writeOptionalString(path)
        out.writeMap(queryParams)
        out.writeMap(headerParams)
        out.writeOptionalString(username)
        out.writeOptionalString(password)
    }

    companion object {
        const val URL = "url"
        const val TYPE = "custom_webhook"
        const val SCHEME_FIELD = "scheme"
        const val HOST_FIELD = "host"
        const val PORT_FIELD = "port"
        const val PATH_FIELD = "path"
        const val QUERY_PARAMS_FIELD = "query_params"
        const val HEADER_PARAMS_FIELD = "header_params"
        const val USERNAME_FIELD = "username"
        const val PASSWORD_FIELD = "password"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): CustomWebhook {
            var url: String? = null
            var scheme: String? = null
            var host: String? = null
            var port: Int = -1
            var path: String? = null
            var queryParams: Map<String, String> = mutableMapOf()
            var headerParams: Map<String, String> = mutableMapOf()
            var username: String? = null
            var password: String? = null

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    URL -> url = xcp.textOrNull()
                    SCHEME_FIELD -> scheme = xcp.textOrNull()
                    HOST_FIELD -> host = xcp.textOrNull()
                    PORT_FIELD -> port = xcp.intValue()
                    PATH_FIELD -> path = xcp.textOrNull()
                    QUERY_PARAMS_FIELD -> queryParams = xcp.mapStrings()
                    HEADER_PARAMS_FIELD -> headerParams = xcp.mapStrings()
                    USERNAME_FIELD -> username = xcp.textOrNull()
                    PASSWORD_FIELD -> password = xcp.textOrNull()
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing custom webhook destination")
                    }
                }
            }
            return CustomWebhook(url, scheme, host, port, path, queryParams, headerParams, username, password)
        }

        @Suppress("UNCHECKED_CAST")
        fun suppressWarning(map: MutableMap<String?, Any?>?): Map<String, String> {
            return map as Map<String, String>
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): CustomWebhook? {
            return if (sin.readBoolean()) {
                CustomWebhook(
                    sin.readString(), // url
                    sin.readOptionalString(), // scheme
                    sin.readString(), // host
                    sin.readOptionalInt(), // port
                    sin.readOptionalString(), // path
                    suppressWarning(sin.readMap()), // queryParams)
                    suppressWarning(sin.readMap()), // headerParams)
                    sin.readOptionalString(), // username
                    sin.readOptionalString() // password
                )
            } else null
        }
    }
}
