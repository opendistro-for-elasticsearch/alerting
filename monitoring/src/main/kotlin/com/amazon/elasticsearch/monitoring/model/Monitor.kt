/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.model.Condition
import com.amazon.elasticsearch.model.Input
import com.amazon.elasticsearch.model.Schedule
import com.amazon.elasticsearch.model.ScheduledJob
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * A value object that represents a Monitor. Monitors are used to periodically execute a source query and check the
 * results.
 */
data class Monitor(override val id: String = NO_ID, override val version: Long = NO_VERSION,
                   override val name: String, override val enabled: Boolean,
                   override val schedule: Schedule, val inputs: List<Input>,
                   val conditions: List<Condition>, val uiMetadata: Map<String, Any>) : ScheduledJob {

    override val type = MONITOR_TYPE

    fun toXContent(builder: XContentBuilder) : XContentBuilder {
        return toXContent(builder, ToXContent.EMPTY_PARAMS)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        if (params.paramAsBoolean("with_type", false)) builder.startObject(type)
        builder.field(TYPE_FIELD, type)
                .field(NAME_FIELD, name)
                .field(ENABLED_FIELD, enabled)
                .field(SCHEDULE_FIELD, schedule)
                .field(INPUTS_FIELD, inputs.toTypedArray())
                .field(TRIGGERS_FIELD, conditions.toTypedArray())
        if (uiMetadata.isNotEmpty()) builder.field(UI_METADATA_FIELD, uiMetadata)
        if (params.paramAsBoolean("with_type", false)) builder.endObject()
        return builder.endObject()
    }

    override fun fromDocument(id: String, version: Long) : Monitor = copy(id = id, version = version)

    companion object {
        const val MONITOR_TYPE = "monitor"
        const val TYPE_FIELD = "type"
        const val NAME_FIELD = "name"
        const val ENABLED_FIELD = "enabled"
        const val SCHEDULE_FIELD = "schedule"
        const val TRIGGERS_FIELD = "triggers"
        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val INPUTS_FIELD = "inputs"
        const val UI_METADATA_FIELD = "ui_metadata"

        // This is defined here instead of in ScheduledJob to avoid having the ScheduledJob class know about all
        // the different subclasses and creating circular dependencies
        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(ScheduledJob::class.java,
                ParseField(MONITOR_TYPE),
                CheckedFunction { parse(it) })

        @JvmStatic @JvmOverloads
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): Monitor {
            lateinit var name : String
            lateinit var schedule: Schedule
            var uiMetadata: Map<String, Any> = mapOf()
            var enabled = true
            var triggers: MutableList<Condition> = mutableListOf()
            var inputs: MutableList<Input> = mutableListOf()

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    NAME_FIELD -> name = xcp.text()
                    ENABLED_FIELD -> enabled = xcp.booleanValue()
                    SCHEDULE_FIELD -> schedule = Schedule.parse(xcp)
                    INPUTS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            inputs.add(Input.parse(xcp))
                        }
                    }
                    TRIGGERS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            triggers.add(Condition.parse(xcp))
                        }
                    }
                    UI_METADATA_FIELD -> {
                        uiMetadata = xcp.map()
                    }
                    else -> {
                        xcp.skipChildren()
                    }
                }
            }

            require(inputs.size == 1) { "Monitors can only have a single search input" }

            return Monitor(id,
                    version,
                    requireNotNull(name) { "Monitor name is null" },
                    enabled,
                    requireNotNull(schedule) { "Monitor schedule is null" },
                    inputs,
                    triggers.toList(),
                    uiMetadata)
        }
    }
}
