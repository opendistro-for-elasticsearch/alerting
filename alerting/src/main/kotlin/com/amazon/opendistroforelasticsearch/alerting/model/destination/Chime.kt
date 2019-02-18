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

import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import java.io.IOException
import java.lang.IllegalStateException

/**
 * A value object that represents a Chime message. Chime message will be
 * submitted to the Chime destination
 */
data class Chime(val url: String) : ToXContent {

    init {
        require(!Strings.isNullOrEmpty(url)) { "URL is null or empty" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(TYPE)
                .field(URL, url)
                .endObject()
    }

    companion object {
        const val URL = "url"
        const val TYPE = "chime"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Chime {
            lateinit var url: String

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    URL -> url = xcp.text()
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing Chime destination")
                    }
                }
            }
            return Chime(url)
        }
    }

    fun constructMessageContent(subject: String?, message: String?): String {
        val messageContent: String? = if (Strings.isNullOrEmpty(subject)) message else "$subject \n\n $message"
        val builder = XContentFactory.contentBuilder(XContentType.JSON)
        builder.startObject()
                .field("Content", messageContent)
                .endObject()
        return builder.string()
    }
}
