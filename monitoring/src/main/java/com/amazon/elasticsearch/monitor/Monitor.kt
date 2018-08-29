package com.amazon.elasticsearch.monitor

import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentType
import java.io.InputStream

// TODO: This class should be replaced by ScheduledJob
/**
 * A value object that represents a Monitor. Monitors are used to periodically execute a search query and check the
 * results.
 */
data class Monitor(val name: String, val enabled: Boolean, val schedule: String, val search: String,
                   val actions: List<String>) {

    companion object {

        @JvmStatic fun fromJson(jsonMonitor : String) =
                fromJson(XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, jsonMonitor))

        @JvmStatic fun fromJson(istream : InputStream) =
                fromJson(XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, istream))

        @JvmStatic fun fromJson(bytes : ByteArray) =
                fromJson(XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, bytes))

        fun fromJson(jp : XContentParser) : Monitor {
            var name : String? = null
            var enabled = true
            var schedule: String? = null
            var search: String? = null
            var actions: MutableList<String> = mutableListOf()

            while (jp.nextToken() != Token.END_OBJECT) {
                val fieldName = jp.currentName()
                jp.nextToken()
                when (fieldName) {
                    "name" -> name = jp.textOrNull()
                    "enabled" -> enabled = jp.booleanValue()
                    "schedule" -> schedule = jp.textOrNull()
                    "search" -> search = jp.textOrNull()
                    "actions" -> {
                        jp.nextToken()
                        while (jp.nextToken() != Token.END_ARRAY) {
                            actions.add(jp.textOrNull())
                        }
                    }
                    else -> {

                        if (jp.currentToken() == Token.START_ARRAY || jp.currentToken() == Token.START_OBJECT) {
                            jp.skipChildren()
                        } else {
                            jp.nextToken()
                        }
                    }
                }
            }

            return Monitor(requireNotNull(name) { "Monitor name is null" },
                    enabled,
                    requireNotNull(schedule) { "Monitor schedule is null" },
                    requireNotNull(search) { "Monitor search is missing" },
                    actions)

        }
    }
}
