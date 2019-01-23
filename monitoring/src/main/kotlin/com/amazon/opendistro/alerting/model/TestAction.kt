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

package com.amazon.opendistro.alerting.model

import com.amazon.opendistro.model.action.Action
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.script.Script
import java.io.IOException

/**
 * An action that doesn't do anything except return it's results when run. Useful for testing Monitors without sending
 * notifications.
 */
data class TestAction(override val name: String, val messageTemplate: Script) : Action {

    override val type: String
        get() = TEST_TYPE

    override fun asTemplateArg(): Map<String, Any> {
        return mapOf("name" to name, "type" to TEST_TYPE)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(type)
                .field(NAME_FIELD, name)
                .field(TEMPLATE_FIELD, messageTemplate)
                .endObject()
                .endObject()
    }

    companion object {
        const val NAME_FIELD = "name"
        const val TEMPLATE_FIELD = "messageTemplate"

        const val TEST_TYPE = "test"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Action::class.java, ParseField(TEST_TYPE),
                CheckedFunction { parseInner(it) })

        @JvmStatic @Throws(IOException::class)
        private fun parseInner(xcp: XContentParser) : TestAction {
            lateinit var name: String
            lateinit var template: Script

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    NAME_FIELD -> name = xcp.text()
                    TEMPLATE_FIELD -> template = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                }
            }
            return TestAction(name, template)
        }

    }
}