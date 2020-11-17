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

package com.amazon.opendistroforelasticsearch.alerting.model

import com.amazon.opendistroforelasticsearch.alerting.core.model.CronSchedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.Input
import com.amazon.opendistroforelasticsearch.alerting.core.model.Schedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.instant
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalTimeField
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalUserField
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.MONITOR_MAX_INPUTS
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.MONITOR_MAX_TRIGGERS
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils.Companion.NO_SCHEMA_VERSION
import com.amazon.opendistroforelasticsearch.alerting.util._ID
import com.amazon.opendistroforelasticsearch.alerting.util._VERSION
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.time.Instant

/**
 * A value object that represents a Monitor. Monitors are used to periodically execute a source query and check the
 * results.
 */
data class Monitor(
    override val id: String = NO_ID,
    override val version: Long = NO_VERSION,
    override val name: String,
    override val enabled: Boolean,
    override val schedule: Schedule,
    override val lastUpdateTime: Instant,
    override val enabledTime: Instant?,
    val user: User?,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val inputs: List<Input>,
    val triggers: List<Trigger>,
    val uiMetadata: Map<String, Any>
) : ScheduledJob {

    override val type = MONITOR_TYPE

    init {
        // Ensure that trigger ids are unique within a monitor
        val triggerIds = mutableSetOf<String>()
        triggers.forEach { trigger ->
            require(triggerIds.add(trigger.id)) { "Duplicate trigger id: ${trigger.id}. Trigger ids must be unique." }
        }
        if (enabled) {
            requireNotNull(enabledTime)
        } else {
            require(enabledTime == null)
        }
        require(inputs.size <= MONITOR_MAX_INPUTS) { "Monitors can only have $MONITOR_MAX_INPUTS search input." }
        require(triggers.size <= MONITOR_MAX_TRIGGERS) { "Monitors can only support up to $MONITOR_MAX_TRIGGERS triggers." }
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        id = sin.readString(),
        version = sin.readLong(),
        name = sin.readString(),
        enabled = sin.readBoolean(),
        schedule = Schedule.readFrom(sin),
        lastUpdateTime = sin.readInstant(),
        enabledTime = sin.readOptionalInstant(),
        user = if (sin.readBoolean()) {
            User(sin)
        } else null,
        schemaVersion = sin.readInt(),
        inputs = sin.readList(::SearchInput),
        triggers = sin.readList(::Trigger),
        uiMetadata = suppressWarning(sin.readMap())
    )
    fun toXContent(builder: XContentBuilder): XContentBuilder {
        return toXContent(builder, ToXContent.EMPTY_PARAMS)
    }

    /** Returns a representation of the monitor suitable for passing into painless and mustache scripts. */
    fun asTemplateArg(): Map<String, Any> {
        return mapOf(_ID to id, _VERSION to version, NAME_FIELD to name, ENABLED_FIELD to enabled)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        if (params.paramAsBoolean("with_type", false)) builder.startObject(type)
        builder.field(TYPE_FIELD, type)
                .field(SCHEMA_VERSION_FIELD, schemaVersion)
                .field(NAME_FIELD, name)
                .optionalUserField(USER_FIELD, user)
                .field(ENABLED_FIELD, enabled)
                .optionalTimeField(ENABLED_TIME_FIELD, enabledTime)
                .field(SCHEDULE_FIELD, schedule)
                .field(INPUTS_FIELD, inputs.toTypedArray())
                .field(TRIGGERS_FIELD, triggers.toTypedArray())
                .optionalTimeField(LAST_UPDATE_TIME_FIELD, lastUpdateTime)
        if (uiMetadata.isNotEmpty()) builder.field(UI_METADATA_FIELD, uiMetadata)
        if (params.paramAsBoolean("with_type", false)) builder.endObject()
        return builder.endObject()
    }

    override fun fromDocument(id: String, version: Long): Monitor = copy(id = id, version = version)

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeLong(version)
        out.writeString(name)
        out.writeBoolean(enabled)
        if (schedule is CronSchedule) {
            out.writeEnum(Schedule.TYPE.CRON)
        } else {
            out.writeEnum(Schedule.TYPE.INTERVAL)
        }
        schedule.writeTo(out)
        out.writeInstant(lastUpdateTime)
        out.writeOptionalInstant(enabledTime)
        out.writeBoolean(user != null)
        user?.writeTo(out)
        out.writeInt(schemaVersion)
        out.writeCollection(inputs)
        out.writeCollection(triggers)
        out.writeMap(uiMetadata)
    }

    companion object {
        const val MONITOR_TYPE = "monitor"
        const val TYPE_FIELD = "type"
        const val SCHEMA_VERSION_FIELD = "schema_version"
        const val NAME_FIELD = "name"
        const val USER_FIELD = "user"
        const val ENABLED_FIELD = "enabled"
        const val SCHEDULE_FIELD = "schedule"
        const val TRIGGERS_FIELD = "triggers"
        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val INPUTS_FIELD = "inputs"
        const val LAST_UPDATE_TIME_FIELD = "last_update_time"
        const val UI_METADATA_FIELD = "ui_metadata"
        const val ENABLED_TIME_FIELD = "enabled_time"

        // This is defined here instead of in ScheduledJob to avoid having the ScheduledJob class know about all
        // the different subclasses and creating circular dependencies
        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(ScheduledJob::class.java,
                ParseField(MONITOR_TYPE),
                CheckedFunction { parse(it) })

        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): Monitor {
            lateinit var name: String
            var user: User? = null
            lateinit var schedule: Schedule
            var lastUpdateTime: Instant? = null
            var enabledTime: Instant? = null
            var uiMetadata: Map<String, Any> = mapOf()
            var enabled = true
            var schemaVersion = NO_SCHEMA_VERSION
            val triggers: MutableList<Trigger> = mutableListOf()
            val inputs: MutableList<Input> = mutableListOf()

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    SCHEMA_VERSION_FIELD -> schemaVersion = xcp.intValue()
                    NAME_FIELD -> name = xcp.text()
                    USER_FIELD -> user = User.parse(xcp)
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
                            triggers.add(Trigger.parse(xcp))
                        }
                    }
                    ENABLED_TIME_FIELD -> enabledTime = xcp.instant()
                    LAST_UPDATE_TIME_FIELD -> lastUpdateTime = xcp.instant()
                    UI_METADATA_FIELD -> uiMetadata = xcp.map()
                    else -> {
                        xcp.skipChildren()
                    }
                }
            }

            if (enabled && enabledTime == null) {
                enabledTime = Instant.now()
            } else if (!enabled) {
                enabledTime = null
            }
            return Monitor(id,
                    version,
                    requireNotNull(name) { "Monitor name is null" },
                    enabled,
                    requireNotNull(schedule) { "Monitor schedule is null" },
                    lastUpdateTime ?: Instant.now(),
                    enabledTime,
                    user,
                    schemaVersion,
                    inputs.toList(),
                    triggers.toList(),
                    uiMetadata)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): Monitor? {
            return Monitor(sin)
        }

        @Suppress("UNCHECKED_CAST")
        fun suppressWarning(map: MutableMap<String?, Any?>?): MutableMap<String, Any> {
            return map as MutableMap<String, Any>
        }
    }
}
