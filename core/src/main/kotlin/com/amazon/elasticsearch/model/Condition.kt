package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.script.Script
import java.io.IOException

data class Condition(val name: String, val severity: Int, val condition: Script, val actions: List<Action>) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .field(NAME_FIELD, name)
                .field(SEVERITY_FILED, severity)
                .startObject(CONDITION_FIELD)
                .field(SCRIPT_FIELD, condition)
                .endObject()
                .field(ACTIONS_FIELD, actions.toTypedArray())
                .endObject()
        return builder
    }

    companion object {
        const val NAME_FIELD = "name"
        const val SEVERITY_FILED = "severity"
        const val CONDITION_FIELD = "condition"
        const val ACTIONS_FIELD = "actions"
        const val SCRIPT_FIELD = "script"

        @JvmStatic @Throws(IOException::class)
        fun parse(xcp: XContentParser) : Condition {
            lateinit var name: String
            var severity = 0
            lateinit var condition: Script
            val actions: MutableList<Action> = mutableListOf()
            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)

            while(xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()

                xcp.nextToken()
                when (fieldName) {
                    NAME_FIELD -> name = xcp.text()
                    SEVERITY_FILED -> severity = xcp.intValue()
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

            return Condition(requireNotNull(name) { "Condition name is null" },
                    requireNotNull(severity) { "Condition severity is null" },
                    requireNotNull(condition) { "Condition is null" },
                    requireNotNull(actions) { "Condition actions are null" })
        }
    }
}