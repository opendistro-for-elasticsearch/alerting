/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.monitoring.alerts.AlertError
import com.amazon.elasticsearch.util.instant
import com.amazon.elasticsearch.util.optionalTimeField
import org.elasticsearch.common.lucene.uid.Versions
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.time.Instant

data class Alert(val id: String = NO_ID, val version: Long = NO_VERSION, val monitorId: String, val monitorName: String,
                 val monitorVersion: Long, val triggerId: String, val triggerName: String,
                 val state: State, val startTime: Instant, val endTime: Instant? = null,
                 val lastNotificationTime: Instant? = null, val acknowledgedTime: Instant? = null,
                 val errorMessage: String? = null, val errorHistory: List<AlertError>,
                 val severity: String) : ToXContent {

    init {
        if (errorMessage != null) {
            require(state == State.ERROR) { "Attempt to create an alert with an error in state: $state" }
        }
    }

    constructor(monitor: Monitor, trigger: Trigger, startTime: Instant, lastNotificationTime: Instant?,
                state: State = State.ACTIVE, errorMessage: String? = null, alertErrors: List<AlertError> = mutableListOf())
            : this(monitorId = monitor.id, monitorName = monitor.name, monitorVersion = monitor.version,
            triggerId = trigger.id, triggerName = trigger.name, state = state, startTime = startTime,
            lastNotificationTime = lastNotificationTime, errorMessage = errorMessage, errorHistory = alertErrors,
            severity = trigger.severity)

    enum class State {
        ACTIVE, ACKNOWLEDGED, COMPLETED, ERROR
    }

    fun isAcknowledged() : Boolean = (state == State.ACKNOWLEDGED)

    companion object {

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

        const val NO_ID = ""
        const val NO_VERSION = Versions.NOT_FOUND

        /**
         * The mapping type of [Alert]s in the ES index.
         *
         * This should go away starting ES 7. We use "_doc" for future compatibility as described here:
         * https://www.elastic.co/guide/en/elasticsearch/reference/6.x/removal-of-types.html#_schedule_for_removal_of_mapping_types
         */
        const val ALERT_TYPE = "_doc"

        @JvmStatic @JvmOverloads
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION) : Alert {

            lateinit var monitorId: String
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

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    MONITOR_ID_FIELD -> monitorId = xcp.text()
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
                }
            }

            return Alert(id = id, version = version, monitorId = requireNotNull(monitorId),
                    monitorName = requireNotNull(monitorName), monitorVersion = monitorVersion,
                    triggerId = requireNotNull(triggerId), triggerName = requireNotNull(triggerName),
                    state = requireNotNull(state), startTime = requireNotNull(startTime), endTime = endTime,
                    lastNotificationTime = lastNotificationTime, acknowledgedTime = acknowledgedTime,
                    errorMessage = errorMessage, errorHistory = errorHistory, severity = severity)
        }

    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field(MONITOR_ID_FIELD, monitorId)
                .field(MONITOR_VERSION_FIELD, monitorVersion)
                .field(MONITOR_NAME_FIELD, monitorName)
                .field(TRIGGER_ID_FIELD, triggerId)
                .field(TRIGGER_NAME_FIELD, triggerName)
                .field(STATE_FIELD, state)
                .field(ERROR_MESSAGE_FIELD, errorMessage)
                .field(SEVERITY_FIELD, severity)
                .field(ALERT_HISTORY_FIELD, errorHistory.toTypedArray())
                .field(Trigger.SEVERITY_FIELD, severity)
                .optionalTimeField(START_TIME_FIELD, startTime)
                .optionalTimeField(LAST_NOTIFICATION_TIME_FIELD, lastNotificationTime)
                .optionalTimeField(END_TIME_FIELD, endTime)
                .optionalTimeField(ACKNOWLEDGED_TIME_FIELD, acknowledgedTime)
                .endObject()
    }

    fun asTemplateArg(): Map<String, Any?> {
        return mapOf("state" to state.toString(),
                "errorMessage" to errorMessage,
                "acknowledgedTime" to acknowledgedTime?.toEpochMilli(),
                "lastNotificationTime" to lastNotificationTime?.toEpochMilli())
    }
}
