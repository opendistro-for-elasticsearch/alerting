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

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertError
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.instant
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalTimeField
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils.Companion.NO_SCHEMA_VERSION
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.lucene.uid.Versions
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.time.Instant

data class Alert(
    val id: String = NO_ID,
    val version: Long = NO_VERSION,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val monitorId: String,
    val monitorName: String,
    val monitorVersion: Long,
    val triggerId: String,
    val triggerName: String,
    val state: State,
    val startTime: Instant,
    val endTime: Instant? = null,
    val lastNotificationTime: Instant? = null,
    val acknowledgedTime: Instant? = null,
    val errorMessage: String? = null,
    val errorHistory: List<AlertError>,
    val severity: String,
    val actionExecutionResults: List<ActionExecutionResult>
) : Writeable, ToXContent {

    init {
        if (errorMessage != null) require(state == State.DELETED || state == State.ERROR) {
            "Attempt to create an alert with an error in state: $state"
        }
    }

    constructor(
        monitor: Monitor,
        trigger: Trigger,
        startTime: Instant,
        lastNotificationTime: Instant?,
        state: State = State.ACTIVE,
        errorMessage: String? = null,
        errorHistory: List<AlertError> = mutableListOf(),
        actionExecutionResults: List<ActionExecutionResult> = mutableListOf(),
        schemaVersion: Int = NO_SCHEMA_VERSION
    ) : this(monitorId = monitor.id, monitorName = monitor.name, monitorVersion = monitor.version,
            triggerId = trigger.id, triggerName = trigger.name, state = state, startTime = startTime,
            lastNotificationTime = lastNotificationTime, errorMessage = errorMessage, errorHistory = errorHistory,
            severity = trigger.severity, actionExecutionResults = actionExecutionResults, schemaVersion = schemaVersion)

    enum class State {
        ACTIVE, ACKNOWLEDGED, COMPLETED, ERROR, DELETED
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
            sin.readString(), // id
            sin.readLong(), // version
            sin.readInt(), // schemaVersion
            sin.readString(), // monitorId
            sin.readString(), // monitorName
            sin.readLong(), // monitorVersion
            sin.readString(), // triggerId
            sin.readString(), // triggerName
            sin.readEnum(State::class.java), // state
            sin.readInstant(), // startTime
            sin.readOptionalInstant(), // endTime
            sin.readOptionalInstant(), // lastNotificationTime
            sin.readOptionalInstant(), // acknowledgedTime
            sin.readOptionalString(), // errorMessage
            sin.readList(::AlertError), // errorHistory
            sin.readString(), // severity
            sin.readList(::ActionExecutionResult) // actionExecutionResults
    )

    fun isAcknowledged(): Boolean = (state == State.ACKNOWLEDGED)

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeLong(version)
        out.writeInt(schemaVersion)
        out.writeString(monitorId)
        out.writeString(monitorName)
        out.writeLong(monitorVersion)
        out.writeString(triggerId)
        out.writeString(triggerName)
        out.writeEnum(state)
        out.writeInstant(startTime)
        out.writeOptionalInstant(endTime)
        out.writeOptionalInstant(lastNotificationTime)
        out.writeOptionalInstant(acknowledgedTime)
        out.writeOptionalString(errorMessage)
        out.writeCollection(errorHistory)
        out.writeString(severity)
        out.writeCollection(actionExecutionResults)
    }

    companion object {

        const val ALERT_ID_FIELD = "id"
        const val SCHEMA_VERSION_FIELD = "schema_version"
        const val ALERT_VERSION_FIELD = "version"
        const val MONITOR_ID_FIELD = "monitor_id"
        const val MONITOR_VERSION_FIELD = "monitor_version"
        const val MONITOR_NAME_FIELD = "monitor_name"
        const val TRIGGER_ID_FIELD = "trigger_id"
        const val TRIGGER_NAME_FIELD = "trigger_name"
        const val STATE_FIELD = "state"
        const val START_TIME_FIELD = "start_time"
        const val LAST_NOTIFICATION_TIME_FIELD = "last_notification_time"
        const val END_TIME_FIELD = "end_time"
        const val ACKNOWLEDGED_TIME_FIELD = "acknowledged_time"
        const val ERROR_MESSAGE_FIELD = "error_message"
        const val ALERT_HISTORY_FIELD = "alert_history"
        const val SEVERITY_FIELD = "severity"
        const val ACTION_EXECUTION_RESULTS_FIELD = "action_execution_results"

        const val NO_ID = ""
        const val NO_VERSION = Versions.NOT_FOUND

        @JvmStatic @JvmOverloads
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): Alert {

            lateinit var monitorId: String
            var schemaVersion = NO_SCHEMA_VERSION
            lateinit var monitorName: String
            var monitorVersion: Long = Versions.NOT_FOUND
            lateinit var triggerId: String
            lateinit var triggerName: String
            lateinit var state: State
            lateinit var startTime: Instant
            lateinit var severity: String
            var endTime: Instant? = null
            var lastNotificationTime: Instant? = null
            var acknowledgedTime: Instant? = null
            var errorMessage: String? = null
            val errorHistory: MutableList<AlertError> = mutableListOf()
            var actionExecutionResults: MutableList<ActionExecutionResult> = mutableListOf()

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    MONITOR_ID_FIELD -> monitorId = xcp.text()
                    SCHEMA_VERSION_FIELD -> schemaVersion = xcp.intValue()
                    MONITOR_NAME_FIELD -> monitorName = xcp.text()
                    MONITOR_VERSION_FIELD -> monitorVersion = xcp.longValue()
                    TRIGGER_ID_FIELD -> triggerId = xcp.text()
                    STATE_FIELD -> state = State.valueOf(xcp.text())
                    TRIGGER_NAME_FIELD -> triggerName = xcp.text()
                    START_TIME_FIELD -> startTime = requireNotNull(xcp.instant())
                    END_TIME_FIELD -> endTime = xcp.instant()
                    LAST_NOTIFICATION_TIME_FIELD -> lastNotificationTime = xcp.instant()
                    ACKNOWLEDGED_TIME_FIELD -> acknowledgedTime = xcp.instant()
                    ERROR_MESSAGE_FIELD -> errorMessage = xcp.textOrNull()
                    ALERT_HISTORY_FIELD -> {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != XContentParser.Token.END_ARRAY) {
                            errorHistory.add(AlertError.parse(xcp))
                        }
                    }
                    SEVERITY_FIELD -> severity = xcp.text()
                    ACTION_EXECUTION_RESULTS_FIELD -> {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != XContentParser.Token.END_ARRAY) {
                            actionExecutionResults.add(ActionExecutionResult.parse(xcp))
                        }
                    }
                }
            }

            return Alert(id = id, version = version, schemaVersion = schemaVersion, monitorId = requireNotNull(monitorId),
                    monitorName = requireNotNull(monitorName), monitorVersion = monitorVersion,
                    triggerId = requireNotNull(triggerId), triggerName = requireNotNull(triggerName),
                    state = requireNotNull(state), startTime = requireNotNull(startTime), endTime = endTime,
                    lastNotificationTime = lastNotificationTime, acknowledgedTime = acknowledgedTime,
                    errorMessage = errorMessage, errorHistory = errorHistory, severity = severity,
                    actionExecutionResults = actionExecutionResults)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): Alert {
            return Alert(sin)
        }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field(MONITOR_ID_FIELD, monitorId)
                .field(SCHEMA_VERSION_FIELD, schemaVersion)
                .field(MONITOR_VERSION_FIELD, monitorVersion)
                .field(MONITOR_NAME_FIELD, monitorName)
                .field(TRIGGER_ID_FIELD, triggerId)
                .field(TRIGGER_NAME_FIELD, triggerName)
                .field(STATE_FIELD, state)
                .field(ERROR_MESSAGE_FIELD, errorMessage)
                .field(ALERT_HISTORY_FIELD, errorHistory.toTypedArray())
                .field(SEVERITY_FIELD, severity)
                .field(ACTION_EXECUTION_RESULTS_FIELD, actionExecutionResults.toTypedArray())
                .optionalTimeField(START_TIME_FIELD, startTime)
                .optionalTimeField(LAST_NOTIFICATION_TIME_FIELD, lastNotificationTime)
                .optionalTimeField(END_TIME_FIELD, endTime)
                .optionalTimeField(ACKNOWLEDGED_TIME_FIELD, acknowledgedTime)
                .endObject()
    }

    fun asTemplateArg(): Map<String, Any?> {
        return mapOf(ACKNOWLEDGED_TIME_FIELD to acknowledgedTime?.toEpochMilli(),
                ALERT_ID_FIELD to id,
                ALERT_VERSION_FIELD to version,
                END_TIME_FIELD to endTime?.toEpochMilli(),
                ERROR_MESSAGE_FIELD to errorMessage,
                LAST_NOTIFICATION_TIME_FIELD to lastNotificationTime?.toEpochMilli(),
                SEVERITY_FIELD to severity,
                START_TIME_FIELD to startTime.toEpochMilli(),
                STATE_FIELD to state.toString())
    }
}
