/*
 *   Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.model

import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.ACTIONS_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.ID_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.NAME_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.SEVERITY_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.UUIDs
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.script.Script
import java.io.IOException

/**
 * A single-alert Trigger that uses Painless scripts which execute on the response of the Monitor input query to define
 * alerting conditions.
 */
data class QueryLevelTrigger(
    override val id: String = UUIDs.base64UUID(),
    override val name: String,
    override val severity: String,
    override val actions: List<Action>,
    val condition: Script
) : Trigger {

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readString(), // id
        sin.readString(), // name
        sin.readString(), // severity
        sin.readList(::Action), // actions
        Script(sin) // condition
    )

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
            .startObject(QUERY_LEVEL_TRIGGER_FIELD)
            .field(ID_FIELD, id)
            .field(NAME_FIELD, name)
            .field(SEVERITY_FIELD, severity)
            .startObject(CONDITION_FIELD)
            .field(SCRIPT_FIELD, condition)
            .endObject()
            .field(ACTIONS_FIELD, actions.toTypedArray())
            .endObject()
            .endObject()
        return builder
    }

    override fun name(): String {
        return QUERY_LEVEL_TRIGGER_FIELD
    }

    /** Returns a representation of the trigger suitable for passing into painless and mustache scripts. */
    fun asTemplateArg(): Map<String, Any> {
        return mapOf(ID_FIELD to id, NAME_FIELD to name, SEVERITY_FIELD to severity,
            ACTIONS_FIELD to actions.map { it.asTemplateArg() })
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeString(name)
        out.writeString(severity)
        out.writeCollection(actions)
        condition.writeTo(out)
    }

    companion object {
        const val QUERY_LEVEL_TRIGGER_FIELD = "query_level_trigger"
        const val CONDITION_FIELD = "condition"
        const val SCRIPT_FIELD = "script"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Trigger::class.java, ParseField(QUERY_LEVEL_TRIGGER_FIELD),
            CheckedFunction { parseInner(it) })

        /**
         * This parse method needs to account for both the old and new Trigger format.
         * In the old format, only one Trigger existed (which is now QueryLevelTrigger) and it was
         * not a named object.
         *
         * The parse() method in the Trigger interface needs to consume the outer START_OBJECT to be able
         * to infer whether it is dealing with the old or new Trigger format. This means that the currentToken at
         * the time this parseInner method is called could differ based on which format is being dealt with.
         *
         * Old Format
         * ----------
         * {
         *   "id": ...,
         *    ^
         *    Current token starts here
         *   "name" ...,
         *   ...
         * }
         *
         * New Format
         * ----------
         * {
         *   "query_level_trigger": {
         *     "id": ...,           ^ Current token starts here
         *     "name": ...,
         *     ...
         *   }
         * }
         *
         * It isn't typically conventional but this parse method will account for both START_OBJECT
         * and FIELD_NAME as the starting token to cover both cases.
         */
        @JvmStatic @Throws(IOException::class)
        fun parseInner(xcp: XContentParser): QueryLevelTrigger {
            var id = UUIDs.base64UUID() // assign a default triggerId if one is not specified
            lateinit var name: String
            lateinit var severity: String
            lateinit var condition: Script
            val actions: MutableList<Action> = mutableListOf()

            if (xcp.currentToken() != Token.START_OBJECT && xcp.currentToken() != Token.FIELD_NAME) {
                XContentParserUtils.throwUnknownToken(xcp.currentToken(), xcp.tokenLocation)
            }

            // If the parser began on START_OBJECT, move to the next token so that the while loop enters on
            // the fieldName (or END_OBJECT if it's empty).
            if (xcp.currentToken() == Token.START_OBJECT) xcp.nextToken()

            while (xcp.currentToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()

                xcp.nextToken()
                when (fieldName) {
                    ID_FIELD -> id = xcp.text()
                    NAME_FIELD -> name = xcp.text()
                    SEVERITY_FIELD -> severity = xcp.text()
                    CONDITION_FIELD -> {
                        xcp.nextToken()
                        condition = Script.parse(xcp)
                        require(condition.lang == Script.DEFAULT_SCRIPT_LANG) {
                            "Invalid script language. Allowed languages are [${Script.DEFAULT_SCRIPT_LANG}]"
                        }
                        xcp.nextToken()
                    }
                    ACTIONS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            actions.add(Action.parse(xcp))
                        }
                    }
                }
                xcp.nextToken()
            }

            return QueryLevelTrigger(
                name = requireNotNull(name) { "Trigger name is null" },
                severity = requireNotNull(severity) { "Trigger severity is null" },
                condition = requireNotNull(condition) { "Trigger is null" },
                actions = requireNotNull(actions) { "Trigger actions are null" },
                id = requireNotNull(id) { "Trigger id is null." })
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): QueryLevelTrigger {
            return QueryLevelTrigger(sin)
        }
    }
}
