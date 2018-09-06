/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitor

import com.amazon.elasticsearch.model.ScheduledJob
import com.amazon.elasticsearch.model.ScheduledJob.Companion.NO_ID
import com.amazon.elasticsearch.model.ScheduledJob.Companion.NO_VERSION
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.*
import org.elasticsearch.common.xcontent.XContentParser.Token

/**
 * A [Monitor] is a [ScheduledJob] used for alarming.
 *
 * Though [ScheduledJob]s support multiple inputs, a [Monitor] (currently) only supports a single input which must be
 * a search query.
 */
data class Monitor(override val id: String = NO_ID,
                   override val version: Long = NO_VERSION,
                   override val name: String,
                   override val enabled: Boolean,
                   override val schedule: String,
                   val search: String,
                   override val triggers: List<String>) : ToXContentObject, ScheduledJob {

    override val inputs: List<String> = listOf(search)

    override val type: ScheduledJob.Type = ScheduledJob.Type.MONITOR

    fun toXContent(builder: XContentBuilder) : XContentBuilder = toXContent(builder, ToXContent.EMPTY_PARAMS)

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        super.toXContent(builder, params)
        builder.field(NAME_FIELD, name)
        builder.field(ENABLED_FIELD, enabled)
        builder.field(SCHEDULE_FIELD, schedule)
        builder.field(SEARCH_FIELD, search)
        builder.startArray(TRIGGERS_FIELD)
        triggers.forEach { builder.value(it) }
        builder.endArray()
        return builder.endObject()
    }

    override fun isFragment(): Boolean = false

    companion object {

        const val NAME_FIELD = "name"
        const val ENABLED_FIELD = "enabled"
        const val SCHEDULE_FIELD = "schedule"
        const val SEARCH_FIELD = "search"
        const val TRIGGERS_FIELD = "triggers"

        @JvmStatic fun fromJson(bytesRef : BytesReference, id: String) =
                fromJson(XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, bytesRef), id)

        @JvmOverloads @JvmStatic
        fun fromJson(jp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION) : Monitor {
            var name : String? = null
            var enabled = true
            var schedule: String? = null
            var search: String? = null
            val triggers: MutableList<String?> = mutableListOf()

            require (jp.nextToken() == Token.START_OBJECT) { "invalid monitor json" }

            while (jp.nextToken() != Token.END_OBJECT) {
                val fieldName = jp.currentName()
                jp.nextToken()

                when (fieldName) {
                    NAME_FIELD     -> name = jp.textOrNull()
                    ENABLED_FIELD  -> enabled = jp.booleanValue()
                    SCHEDULE_FIELD -> schedule = jp.textOrNull()
                    SEARCH_FIELD   -> search = jp.textOrNull()
                    TRIGGERS_FIELD  -> {
                        while (jp.nextToken() != Token.END_ARRAY) {
                            triggers.add(jp.textOrNull())
                        }
                    }
                    else -> jp.skipChildren()
                }
            }

            return Monitor(id,
                    version,
                    requireNotNull(name) { "Monitor name is null" },
                    enabled,
                    requireNotNull(schedule) { "Monitor schedule is null" },
                    requireNotNull(search) { "Monitor search is missing" },
                    triggers.filterNotNull())

        }
    }
}
