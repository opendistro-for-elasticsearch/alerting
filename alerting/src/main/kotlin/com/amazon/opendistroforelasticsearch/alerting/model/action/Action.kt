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

import org.elasticsearch.common.UUIDs
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.script.Script
import java.io.IOException
import java.lang.IllegalStateException

/**
 * This class holds the data and parser logic for Action which is part of a trigger
 */
data class Action(
    val name: String,
    val destinationId: String,
    val subjectTemplate: Script?,
    val messageTemplate: Script,
    val throttleEnabled: Boolean,
    val throttle: Throttle?,
    val id: String = UUIDs.base64UUID()
) : ToXContentObject {

    init {
        if (subjectTemplate != null) {
            require(subjectTemplate.lang == Action.MUSTACHE) { "subject_template must be a mustache script" }
        }
        require(messageTemplate.lang == Action.MUSTACHE) { "message_template must be a mustache script" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field(ID_FIELD, id)
                .field(NAME_FIELD, name)
                .field(DESTINATION_ID_FIELD, destinationId)
                .field(SUBJECT_TEMPLATE_FIELD, subjectTemplate)
                .field(MESSAGE_TEMPLATE_FIELD, messageTemplate)
                .field(THROTTLE_ENABLED_FIELD, throttleEnabled)
                .field(THROTTLE_FIELD, throttle)
                .endObject()
    }

    fun asTemplateArg(): Map<String, Any> {
        return mapOf(NAME_FIELD to name)
    }

    companion object {
        const val ID_FIELD = "id"
        const val NAME_FIELD = "name"
        const val DESTINATION_ID_FIELD = "destination_id"
        const val SUBJECT_TEMPLATE_FIELD = "subject_template"
        const val MESSAGE_TEMPLATE_FIELD = "message_template"
        const val THROTTLE_ENABLED_FIELD = "throttle_enabled"
        const val THROTTLE_FIELD = "throttle"
        const val MUSTACHE = "mustache"
        const val SUBJECT = "subject"
        const val MESSAGE = "message"
        const val MESSAGE_ID = "messageId"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Action {
            var id = UUIDs.base64UUID() // assign a default action id if one is not specified
            lateinit var name: String
            lateinit var destinationId: String
            var subjectTemplate: Script? = null // subject template could be null for some destinations
            lateinit var messageTemplate: Script
            var throttleEnabled = false
            var throttle: Throttle? = null

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    ID_FIELD -> id = xcp.text()
                    NAME_FIELD -> name = xcp.textOrNull()
                    DESTINATION_ID_FIELD -> destinationId = xcp.textOrNull()
                    SUBJECT_TEMPLATE_FIELD -> subjectTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                    MESSAGE_TEMPLATE_FIELD -> messageTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                    THROTTLE_FIELD -> throttle = Throttle.parse(xcp)
                    THROTTLE_ENABLED_FIELD -> {
                        throttleEnabled = xcp.booleanValue()
                    }

                    else -> {
                        throw IllegalStateException("Unexpected field: $fieldName, while parsing action")
                    }
                }
            }

            return Action(requireNotNull(name) { "Action name is null" },
                    requireNotNull(destinationId) { "Destination id is null" },
                    subjectTemplate,
                    requireNotNull(messageTemplate) { "Action message template is null" },
                    throttleEnabled,
                    throttle,
                    id = requireNotNull(id))
        }
    }
}
