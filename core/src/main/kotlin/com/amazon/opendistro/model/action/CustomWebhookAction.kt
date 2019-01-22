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

package com.amazon.opendistro.model.action

import com.amazon.opendistro.model.action.Action
import com.amazon.opendistro.model.action.Action.Companion.MUSTACHE
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.script.Script
import java.io.IOException

/**
 * This class holds the data and parser for Custom webhook message which will be
 * submitted to the custom webhook destination
 */
data class CustomWebhookAction(override val name: String,
                       val destinationId: String,
                       val messageTemplate: Script,
                       override val type: String = CUSTOM_WEBHOOK_TYPE) : Action {

    init {
        require(messageTemplate.lang == MUSTACHE) { "message_template must be a mustache script" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(type)
                .field(NAME_FIELD, name)
                .field(DESTINATION_ID_FIELD, destinationId)
                .field(MESSAGE_TEMPLATE_FIELD, messageTemplate)
                .endObject()
                .endObject()
    }

    override fun asTemplateArg(): Map<String, Any> {
        return mapOf(NAME_FIELD to name, TYPE_FIELD to type)
    }

    companion object {
        const val NAME_FIELD = "name"
        const val TYPE_FIELD = "type"
        const val DESTINATION_ID_FIELD = "destination_id"
        const val MESSAGE_TEMPLATE_FIELD = "message_template"
        const val CUSTOM_WEBHOOK_TYPE = "custom_webhook"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Action::class.java,
                ParseField(CUSTOM_WEBHOOK_TYPE),
                CheckedFunction { parseInner(it) })

        @JvmStatic @Throws(IOException::class)
        private fun parseInner(xcp: XContentParser): CustomWebhookAction {
            lateinit var name: String
            lateinit var destinationId: String
            lateinit var messageTemplate: Script

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while(xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    NAME_FIELD -> name = xcp.textOrNull()
                    DESTINATION_ID_FIELD -> destinationId = xcp.textOrNull()
                    MESSAGE_TEMPLATE_FIELD -> messageTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                }
            }

            return CustomWebhookAction(requireNotNull(name) { "customWebhook Action name is null" },
                    requireNotNull(destinationId) { "customWebhook Action destinationId is null" },
                    requireNotNull(messageTemplate) { "customWebhook Action message_temaplate is null" })
        }
    }
}
