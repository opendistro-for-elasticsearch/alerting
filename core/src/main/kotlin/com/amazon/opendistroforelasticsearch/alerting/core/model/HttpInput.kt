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

package com.amazon.opendistroforelasticsearch.alerting.core.model

import org.apache.commons.validator.routines.UrlValidator
import org.apache.http.client.utils.URIBuilder
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.Strings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import java.io.IOException

/**
 * This is a data class of HTTP type of input for Monitors.
 */
data class HttpInput(
    val scheme: String,
    val host: String?,
    val port: Int,
    val path: String?,
    val params: Map<String, String>,
    val url: String,
    val connection_timeout: Int,
    val socket_timeout: Int
) : Input {

    // Verify parameters are valid during creation
    init {
        require(connection_timeout > 0) {
            "Connection timeout: $connection_timeout is not greater than 0."
        }
        require(socket_timeout > 0) {
            "Socket timeout: $socket_timeout is not greater than 0."
        }

        // Create an UrlValidator that only accepts "http" and "https" as valid scheme and allows local URLs.
        val urlValidator = UrlValidator(arrayOf("http", "https"), UrlValidator.ALLOW_LOCAL_URLS)

        // Build url field by field if not provided as whole, and update url field.
        val constructedUrl = if (Strings.isNullOrEmpty(url)) {
            val uriBuilder = URIBuilder()
            uriBuilder.scheme = scheme
            uriBuilder.host = host
            uriBuilder.port = port
            uriBuilder.path = path
            for (e in params.entries)
                uriBuilder.addParameter(e.key, e.value)
            uriBuilder.build().toString()
        } else {
            url
        }

        require(urlValidator.isValid(constructedUrl)) {
            "Invalid url: $constructedUrl"
        }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(HTTP_FIELD)
                .field(SCHEME_FIELD, scheme)
                .field(HOST_FIELD, host)
                .field(PORT_FIELD, port)
                .field(PATH_FIELD, path)
                .field(PARAMS_FIELD, this.params)
                .field(URL_FIELD, url)
                .field(CONNECTION_TIMEOUT_FIELD, connection_timeout)
                .field(SOCKET_TIMEOUT_FIELD, socket_timeout)
                .endObject()
                .endObject()
    }

    override fun name(): String {
        return HTTP_FIELD
    }

    companion object {
        const val SCHEME_FIELD = "scheme"
        const val HOST_FIELD = "host"
        const val PORT_FIELD = "port"
        const val PATH_FIELD = "path"
        const val PARAMS_FIELD = "params"
        const val URL_FIELD = "url"
        const val CONNECTION_TIMEOUT_FIELD = "connection_timeout"
        const val SOCKET_TIMEOUT_FIELD = "socket_timeout"
        const val HTTP_FIELD = "http"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Input::class.java, ParseField("http"), CheckedFunction { parseInner(it) })

        /**
         * This parse function uses [XContentParser] to parse JSON input and store corresponding fields to create a [HttpInput] object
         */
        @JvmStatic @Throws(IOException::class)
        private fun parseInner(xcp: XContentParser): HttpInput {
            var scheme = "http"
            var host: String? = null
            var port: Int = -1
            var path: String? = null
            var params: Map<String, String> = mutableMapOf()
            var url = ""
            var connectionTimeout = TimeValue.timeValueSeconds(10).millis().toInt()
            var socketTimeout = TimeValue.timeValueSeconds(10).millis().toInt()

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)

            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    SCHEME_FIELD -> scheme = xcp.text()
                    HOST_FIELD -> host = xcp.text()
                    PORT_FIELD -> port = xcp.intValue()
                    PATH_FIELD -> path = xcp.text()
                    PARAMS_FIELD -> params = xcp.mapStrings()
                    URL_FIELD -> url = xcp.text()
                    CONNECTION_TIMEOUT_FIELD -> connectionTimeout = xcp.intValue()
                    SOCKET_TIMEOUT_FIELD -> socketTimeout = xcp.intValue()
                }
            }
            return HttpInput(scheme, host, port, path, params, url, connectionTimeout, socketTimeout)
        }
    }
}
