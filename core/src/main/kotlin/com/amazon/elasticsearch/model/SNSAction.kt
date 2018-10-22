/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.model

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

data class SNSAction(override val name: String,
                     val topicARN: String,
                     val subjectTemplate: Script,
                     val messageTemplate: Script,
                     override val type: String = SNS_TYPE
                     ) : Action {

    init {
        require(subjectTemplate.lang == "mustache") { "subject_template must be a mustache script" }
        require(messageTemplate.lang == "mustache") { "message_template must be a mustache script" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(type)
                .field(NAME_FIELD, name)
                .field(TOPIC_ARN_FIELD, topicARN)
                .field(SUBJECT_TEMPLATE_FIELD, subjectTemplate)
                .field(MESSAGE_TEMPLATE_FIELD, messageTemplate)
                .endObject()
                .endObject()
    }

    override fun asTemplateArg(): Map<String, Any> {
        return mapOf(NAME_FIELD to name, TYPE_FIELD to type, TOPIC_ARN_FIELD to topicARN)
    }

    companion object {
        const val NAME_FIELD = "name"
        const val TYPE_FIELD = "type"
        const val TOPIC_ARN_FIELD = "topic_arn"
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
            lateinit var subjectTemplate: Script
            lateinit var messageTemplate: Script

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while(xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    NAME_FIELD -> name = xcp.textOrNull()
                    TOPIC_ARN_FIELD -> topicARN = xcp.textOrNull()
                    SUBJECT_TEMPLATE_FIELD -> subjectTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                    MESSAGE_TEMPLATE_FIELD -> messageTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                }
            }

            return SNSAction(requireNotNull(name) { "SNS Action name is null" },
                    requireNotNull(topicARN) { "SNS Action topic_arn is null" },
                    requireNotNull(subjectTemplate) { "SNS Action subject_template is null" },
                    requireNotNull(messageTemplate) { "SNS Action message_temaplte is null" })
        }
    }
}