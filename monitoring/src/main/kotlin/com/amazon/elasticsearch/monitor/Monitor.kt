package com.amazon.elasticsearch.monitor

import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.*
import org.elasticsearch.common.xcontent.XContentParser.Token

// TODO: This class should be replaced by ScheduledJob
/**
 * A value object that represents a Monitor. Monitors are used to periodically execute a search query and check the
 * results.
 */
data class Monitor(val id: String, val name: String, val enabled: Boolean, val schedule: String, val search: String,
                   val actions: List<String>) : ToXContentObject {

    fun toXContent(builder: XContentBuilder) : XContentBuilder {
        return toXContent(builder, ToXContent.EMPTY_PARAMS)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        builder.field(NAME_FIELD, name)
        builder.field(ENABLED_FIELD, enabled)
        builder.field(SCHEDULE_FIELD, schedule)
        builder.field(SEARCH_FIELD, search)
        builder.startArray(ACTIONS_FIELD)
        actions.forEach { builder.value(it) }
        builder.endArray()
        return builder.endObject()
    }

    companion object {

        const val NAME_FIELD = "name"
        const val ENABLED_FIELD = "enabled"
        const val SCHEDULE_FIELD = "schedule"
        const val SEARCH_FIELD = "search"
        const val ACTIONS_FIELD = "actions"
        const val NO_ID = ""

        @JvmStatic fun fromJson(bytesRef : BytesReference, id: String) =
                fromJson(XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, bytesRef), id)

        @JvmOverloads @JvmStatic fun fromJson(jp: XContentParser, id: String = NO_ID) : Monitor {
            var name : String? = null
            var enabled = true
            var schedule: String? = null
            var search: String? = null
            var actions: MutableList<String?> = mutableListOf()

            require (jp.nextToken() == Token.START_OBJECT) { "invalid monitor json" }

            while (jp.nextToken() != Token.END_OBJECT) {
                val fieldName = jp.currentName()
                jp.nextToken()

                when (fieldName) {
                    NAME_FIELD     -> name = jp.textOrNull()
                    ENABLED_FIELD  -> enabled = jp.booleanValue()
                    SCHEDULE_FIELD -> schedule = jp.textOrNull()
                    SEARCH_FIELD   -> search = jp.textOrNull()
                    ACTIONS_FIELD  -> {
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

            return Monitor(id,
                    requireNotNull(name) { "Monitor name is null" },
                    enabled,
                    requireNotNull(schedule) { "Monitor schedule is null" },
                    requireNotNull(search) { "Monitor search is missing" },
                    actions.filterNotNull())

        }
    }
}
