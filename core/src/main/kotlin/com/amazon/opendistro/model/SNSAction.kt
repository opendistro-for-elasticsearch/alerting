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

package com.amazon.opendistro.model

import com.amazon.opendistro.model.action.Action
import com.amazon.opendistro.model.action.Action.Companion.MUSTACHE
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.script.Script
import java.io.IOException
import java.util.regex.Pattern

/**
 * This class holds the data and parser for SNS message which will be
 * submitted to the SNS destination
 */
data class SNSAction(override val name: String,
                     val topicARN: String,
                     val roleARN: String,
                     val subjectTemplate: Script,
                     val messageTemplate: Script,
                     override val type: String = SNS_TYPE
                     ) : Action {

    init {
        require(subjectTemplate.lang == MUSTACHE) { "subject_template must be a mustache script" }
        require(messageTemplate.lang == MUSTACHE) { "message_template must be a mustache script" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(type)
                .field(NAME_FIELD, name)
                .field(TOPIC_ARN_FIELD, topicARN)
                .field(ROLE_ARN_FIELD, roleARN)
                .field(SUBJECT_TEMPLATE_FIELD, subjectTemplate)
                .field(MESSAGE_TEMPLATE_FIELD, messageTemplate)
                .endObject()
                .endObject()
    }

    override fun asTemplateArg(): Map<String, Any> {
        return mapOf(NAME_FIELD to name, TYPE_FIELD to type, TOPIC_ARN_FIELD to topicARN)
    }

    companion object {
        private val SNS_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:sns:([a-zA-Z0-9-]+):([0-9]{12}):([a-zA-Z0-9-_]+)$")
        private val IAM_ARN_REGEX = Pattern.compile("^arn:aws(-[^:]+)?:iam::([0-9]{12}):([a-zA-Z0-9-/_]+)$")

        const val NAME_FIELD = "name"
        const val TYPE_FIELD = "type"
        const val TOPIC_ARN_FIELD = "topic_arn"
        const val ROLE_ARN_FIELD = "role_arn"
        const val SUBJECT_TEMPLATE_FIELD = "subject_template"
        const val MESSAGE_TEMPLATE_FIELD = "message_template"
        const val SNS_TYPE = "sns"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Action::class.java,
                ParseField(SNS_TYPE),
                CheckedFunction { parseInner(it) })

        @JvmStatic @Throws(IOException::class)
        private fun parseInner(xcp: XContentParser): SNSAction {
            lateinit var name: String
            lateinit var topicARN: String
            lateinit var roleARN: String
            lateinit var subjectTemplate: Script
            lateinit var messageTemplate: Script

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while(xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    NAME_FIELD -> name = xcp.textOrNull()
                    TOPIC_ARN_FIELD -> topicARN = xcp.textOrNull()
                    ROLE_ARN_FIELD -> roleARN = xcp.textOrNull()
                    SUBJECT_TEMPLATE_FIELD -> subjectTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                    MESSAGE_TEMPLATE_FIELD -> messageTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                }
            }

            require(SNS_ARN_REGEX.matcher(topicARN).find()) { "Invalid AWS SNS topic ARN: $topicARN" }
            require(IAM_ARN_REGEX.matcher(roleARN).find()) { "Invalid AWS role ARN: $roleARN " }

            return SNSAction(requireNotNull(name) { "SNS Action name is null" },
                    requireNotNull(topicARN) { "SNS Action topic_arn is null" },
                    requireNotNull(roleARN) { "SNS Action role_arn is null" },
                    requireNotNull(subjectTemplate) { "SNS Action subject_template is null" },
                    requireNotNull(messageTemplate) { "SNS Action message_template is null" })
        }
    }
}
