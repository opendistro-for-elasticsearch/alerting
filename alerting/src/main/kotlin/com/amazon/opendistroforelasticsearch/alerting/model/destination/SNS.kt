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

import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalStringField
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import java.io.IOException
import java.lang.IllegalStateException
import java.util.regex.Pattern

data class SNS(val topicARN: String, val roleARN: String?) : ToXContent {

    init {
        require(SNS_ARN_REGEX.matcher(topicARN).find()) { "Invalid AWS SNS topic ARN: $topicARN" }
        if (roleARN != null) {
            require(IAM_ARN_REGEX.matcher(roleARN).find()) { "Invalid AWS role ARN: $roleARN " }
        }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(SNS_TYPE)
                .field(TOPIC_ARN_FIELD, topicARN)
                .optionalStringField(ROLE_ARN_FIELD, roleARN)
                .endObject()
    }

    @Throws(IOException::class)
    fun writeTo(out: StreamOutput) {
        out.writeString(topicARN)
        out.writeOptionalString(roleARN)
    }

    companion object {

        private val SNS_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:sns:([a-zA-Z0-9-]+):([0-9]{12}):([a-zA-Z0-9-_]+)$")
        private val IAM_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:iam::([0-9]{12}):([a-zA-Z_0-9+=,.@\\-_/]+)$")

        const val TOPIC_ARN_FIELD = "topic_arn"
        const val ROLE_ARN_FIELD = "role_arn"
        const val SNS_TYPE = "sns"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): SNS {
            lateinit var topicARN: String
            var roleARN: String? = null

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    TOPIC_ARN_FIELD -> topicARN = xcp.textOrNull()
                    ROLE_ARN_FIELD -> roleARN = xcp.textOrNull()
                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing SNS destination")
                    }
                }
            }
            if (DestinationType.snsUseIamRole) {
                requireNotNull(roleARN) { "SNS Action role_arn is null" }
            }
            return SNS(requireNotNull(topicARN) { "SNS Action topic_arn is null" }, roleARN)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): SNS? {
            return if (sin.readBoolean()) {
                SNS(
                    topicARN = sin.readString(),
                    roleARN = sin.readOptionalString()
                )
            } else null
        }
    }
}
