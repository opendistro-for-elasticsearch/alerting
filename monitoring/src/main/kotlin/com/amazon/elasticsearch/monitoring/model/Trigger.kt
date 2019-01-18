/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.model.action.Action
import org.elasticsearch.common.UUIDs
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.script.Script
import java.io.IOException

data class Trigger( val name: String, val severity: String, val condition: Script, val actions: List<Action>,
                    val id: String = UUIDs.base64UUID()) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .field(ID_FIELD, id)
                .field(NAME_FIELD, name)
                .field(SEVERITY_FIELD, severity)
                .startObject(CONDITION_FIELD)
                .field(SCRIPT_FIELD, condition)
                .endObject()
                .field(ACTIONS_FIELD, actions.toTypedArray())
                .endObject()
        return builder
    }

    /** Returns a representation of the trigger suitable for passing into painless and mustache scripts. */
    fun asTemplateArg() : Map<String, Any> {
        return mapOf(ID_FIELD to id, NAME_FIELD to name, SEVERITY_FIELD to severity,
                ACTIONS_FIELD to actions.map { it.asTemplateArg() })
    }

    companion object {
        const val ID_FIELD = "id"
        const val NAME_FIELD = "name"
        const val SEVERITY_FIELD = "severity"
        const val CONDITION_FIELD = "condition"
        const val ACTIONS_FIELD = "actions"
        const val SCRIPT_FIELD = "script"

        @JvmStatic @Throws(IOException::class)
        fun parse(xcp: XContentParser) : Trigger {
            var id = UUIDs.base64UUID() // assign a default triggerId if one is not specified
            lateinit var name: String
            lateinit var severity: String
            lateinit var condition: Script
            val actions: MutableList<Action> = mutableListOf()
            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)

            while(xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()

                xcp.nextToken()
                when (fieldName) {
                    ID_FIELD -> id = xcp.text()
                    NAME_FIELD -> name = xcp.text()
                    SEVERITY_FIELD -> severity = xcp.text()
                    CONDITION_FIELD -> {
                        xcp.nextToken()
                        condition = Script.parse(xcp)
                        require(condition.lang == Script.DEFAULT_SCRIPT_LANG) { "Invalid script language. Allowed languages are [${Script.DEFAULT_SCRIPT_LANG}]" }
                        xcp.nextToken()
                    }
                    ACTIONS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            actions.add(Action.parse(xcp))
                        }
                    }
                }
            }

            return Trigger(
                    name = requireNotNull(name) { "Trigger name is null" },
                    severity = requireNotNull(severity) { "Trigger severity is null" },
                    condition = requireNotNull(condition) { "Trigger is null" },
                    actions = requireNotNull(actions) { "Trigger actions are null" },
                    id = requireNotNull(id) { "Trigger id is null." })
        }
    }
}