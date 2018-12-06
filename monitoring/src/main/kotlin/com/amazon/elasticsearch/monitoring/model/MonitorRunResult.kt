/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.util.optionalTimeField
import com.amazon.elasticsearch.util.stackTraceString
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import java.time.Instant

data class MonitorRunResult(val monitorName: String, val periodStart: Instant, val periodEnd: Instant,
                            val error: Exception? = null,
                            val triggerResults : Map<String, TriggerRunResult> = mapOf()) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("monitor_name", monitorName)
                .optionalTimeField("period_start", periodStart)
                .optionalTimeField("period_end", periodEnd)
                .field("error", error?.stackTraceString())
                .field("trigger_results", triggerResults)
                .endObject()
    }

    /** Returns error information to store in the Alert. Currently it's just the stack trace but it can be more */
    fun alertError() : String? = error?.stackTraceString()
}

data class TriggerRunResult(val triggerName: String, val triggered: Boolean, val error: Exception? = null,
                            val actionResults : MutableMap<String, ActionRunResult> = mutableMapOf()) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("name", triggerName)
                .field("triggered", triggered)
                .field("error", error?.stackTraceString())
                .field("action_results", actionResults as Map<String, ActionRunResult>)
                .endObject()
    }

    /** Returns error information to store in the Alert. Currently it's just the stack trace but it can be more */
    fun alertError() : String? {
        if (error != null) return error.stackTraceString()
        for (actionResult in actionResults.values) {
            if (actionResult.error != null) return actionResult.error.stackTraceString()
        }
        return null
    }
}

data class ActionRunResult(val actionName: String, val output: Map<String, String>, val throttled: Boolean = false,
                           val error: Exception? = null) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("name", actionName)
                .field("output", output)
                .field("throttled", throttled)
                .field("error", error?.stackTraceString())
                .endObject()
    }
}