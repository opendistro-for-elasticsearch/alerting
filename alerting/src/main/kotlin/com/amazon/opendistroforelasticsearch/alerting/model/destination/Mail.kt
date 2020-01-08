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
    val recipients: String?
) : ToXContent {

    init {
        require(!Strings.isNullOrEmpty(recipients)) {
            "Comma separated recipients must be provided."
        }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(TYPE)
                .field(RECIPIENTS_FIELD, recipients)
                .endObject()
    }

    companion object {
        const val TYPE = "mail"
        const val RECIPIENTS_FIELD = "recipients"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Mail {
            var recipients: String? = null

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    RECIPIENTS_FIELD -> recipients = xcp.textOrNull()
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing mail destination")
                    }
                }
            }
            return Mail(recipients)
        }
    }
}
