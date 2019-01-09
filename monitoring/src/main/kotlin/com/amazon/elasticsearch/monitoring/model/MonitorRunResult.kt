/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.monitoring.alerts.AlertError
import com.amazon.elasticsearch.util.optionalTimeField
import com.amazon.elasticsearch.util.stackTraceString
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import java.time.Instant

data class MonitorRunResult(val monitorName: String, val periodStart: Instant, val periodEnd: Instant,
                            val error: Exception? = null, val inputResults: InputRunResults = InputRunResults(),
                            val triggerResults : Map<String, TriggerRunResult> = mapOf()) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("monitor_name", monitorName)
                .optionalTimeField("period_start", periodStart)
                .optionalTimeField("period_end", periodEnd)
                .field("error", error?.stackTraceString())
                .field("input_results", inputResults)
                .field("trigger_results", triggerResults)
                .endObject()
    }

    /** Returns error information to store in the Alert. Currently it's just the stack trace but it can be more */
    fun alertError() : AlertError? {
        if (error != null)
            return AlertError(Instant.now(), "Error running monitor: \n" + error.stackTraceString())
        if (inputResults.error != null)
            return AlertError(Instant.now(), "Error fetching inputs: \n" + inputResults.error.stackTraceString())
        return null
    }

    fun scriptContextError(trigger: Trigger): Exception? {
        return error ?: inputResults.error ?: triggerResults[trigger.id]?.error
    }
}

data class InputRunResults(val results: List<Map<String, Any>> = listOf(), val error: Exception? = null) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("results", results)
                .field("error", error?.stackTraceString())
                .endObject()
    }
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
    fun alertError() : AlertError? {
        if (error != null) return AlertError(Instant.now(), "Error evaluating trigger: \n" + error.stackTraceString())
        for (actionResult in actionResults.values) {
            if (actionResult.error != null) {
                return AlertError(Instant.now(), "Error running action: \n" + actionResult.error.stackTraceString())
            }
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