/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import org.elasticsearch.common.lucene.uid.Versions
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import java.io.IOException
import java.util.Date

data class Alert(val id: String = NO_ID, val version: Long = NO_VERSION, val monitorId: String, val monitorName: String,
                 val monitorVersion: Long, val triggerId: String, val triggerName: String,
                 val state: State, val startTime: Date, val endTime: Date? = null,
                 val lastNotificationTime: Date? = null, val acknowledgedTime: Date? = null,
                 val errorMessage: String? = null) : ToXContent {

    constructor(monitor: Monitor, trigger: Trigger, startTime: Date) : this(monitorId = monitor.id,
            monitorName = monitor.name, monitorVersion = monitor.version, triggerId = trigger.id,
            triggerName = trigger.name, state = State.ACTIVE, startTime = startTime)

    enum class State {
        ACTIVE, ACKNOWLEDGED, COMPLETED, ERROR
    }

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
            lateinit var startTime: Date
            var endTime: Date? = null
            var lastNotificationTime: Date? = null
            var acknowledgedTime: Date? = null
            var errorMessage: String? = null

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
                    START_TIME_FIELD -> startTime = requireNotNull(xcp.date())
                    END_TIME_FIELD -> endTime = xcp.date()
                    LAST_NOTIFICATION_TIME_FIELD -> lastNotificationTime = xcp.date()
                    ACKNOWLEDGED_TIME_FIELD -> acknowledgedTime = xcp.date()
                    ERROR_MESSAGE_FIELD -> errorMessage = xcp.textOrNull()
                }
            }

            return Alert(id = id, version = version, monitorId = requireNotNull(monitorId),
                    monitorName = requireNotNull(monitorName), monitorVersion = monitorVersion,
                    triggerId = requireNotNull(triggerId), triggerName = requireNotNull(triggerName),
                    state = requireNotNull(state), startTime = startTime, endTime = endTime,
                    lastNotificationTime = lastNotificationTime, acknowledgedTime = acknowledgedTime,
                    errorMessage = errorMessage)
        }

    }

    fun isAcknowledged() : Boolean {
        return acknowledgedTime != null
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
                .dateField(START_TIME_FIELD, START_TIME_FIELD, startTime.time)
                .optionalDateField(LAST_NOTIFICATION_TIME_FIELD, lastNotificationTime)
                .optionalDateField(END_TIME_FIELD, endTime)
                .optionalDateField(ACKNOWLEDGED_TIME_FIELD, acknowledgedTime)
                .endObject()
    }

    fun asTemplateArg(): Map<String, Any?> {
        return mapOf("state" to state.toString(),
                "errorMessage" to errorMessage,
                "acknowledgedTime" to acknowledgedTime?.time,
                "lastNotificationTime" to lastNotificationTime?.time)
    }
}

private fun XContentBuilder.optionalDateField(name: String, date: Date?) : XContentBuilder {
    if (date == null) {
        return nullField(name)
    }
    return dateField(name, name, date.time)
}

private fun XContentParser.date() : Date? {
    return when {
        currentToken() == XContentParser.Token.VALUE_NULL -> null
        currentToken().isValue -> Date(longValue())
        else -> {
            XContentParserUtils.throwUnknownToken(currentToken(), tokenLocation)
            null // unreachable
        }
    }
}