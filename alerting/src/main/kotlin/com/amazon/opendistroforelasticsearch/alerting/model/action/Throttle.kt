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

package com.amazon.opendistroforelasticsearch.alerting.model.action

import org.apache.commons.codec.binary.StringUtils
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import java.io.IOException
import java.lang.IllegalStateException
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Throttle(
    val value: Int,
    val unit: ChronoUnit
) : ToXContentObject {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field(VALUE_FIELD, value)
                .field(UNIT_FIELD, unit.name)
                .endObject()
    }

    companion object {
        const val VALUE_FIELD = "value"
        const val UNIT_FIELD = "unit"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Throttle {
            var value: Int = 0
            var unit: ChronoUnit = ChronoUnit.MINUTES // only support MINUTES throttle unit currently

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    UNIT_FIELD -> {
                        val unitString = xcp.text().toUpperCase(Locale.ROOT)
                        require(StringUtils.equals(unitString, ChronoUnit.MINUTES.name), { "Only support MINUTES throttle unit currently" })
                        unit = ChronoUnit.valueOf(unitString)
                    }
                    VALUE_FIELD -> {
                        val currentToken = xcp.currentToken()
                        require(currentToken != XContentParser.Token.VALUE_NULL, { "Throttle value can't be null" })
                        when {
                            currentToken.isValue -> {
                                value = xcp.intValue()
                                require(value > 0, { "Can only set positive throttle period" })
                            }
                            else -> {
                                XContentParserUtils.throwUnknownToken(currentToken, xcp.tokenLocation)
                            }
                        }
                    }

                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing action")
                    }
                }
            }
            return Throttle(value = value, unit = requireNotNull(unit))
        }
    }
}
