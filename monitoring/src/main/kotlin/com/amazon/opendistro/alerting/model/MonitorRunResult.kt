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

package com.amazon.opendistro.alerting.model

import com.amazon.opendistro.alerting.alerts.AlertError
import com.amazon.opendistro.util.optionalTimeField
import com.amazon.opendistro.util.stackTraceString
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