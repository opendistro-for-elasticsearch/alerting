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
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalTimeField
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.script.ScriptException
import java.time.Instant

data class MonitorRunResult(
    val monitorName: String,
    val periodStart: Instant,
    val periodEnd: Instant,
    val error: Exception? = null,
    val inputResults: InputRunResults = InputRunResults(),
    val triggerResults: Map<String, TriggerRunResult> = mapOf()
) : ToXContent {
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("monitor_name", monitorName)
                .optionalTimeField("period_start", periodStart)
                .optionalTimeField("period_end", periodEnd)
                .field("error", error?.message)
                .field("input_results", inputResults)
                .field("trigger_results", triggerResults)
                .endObject()
    }

    /** Returns error information to store in the Alert. Currently it's just the stack trace but it can be more */
    fun alertError(): AlertError? {
        if (error != null) {
            return AlertError(Instant.now(), "Error running monitor:\n${error.userErrorMessage()}")
        }

        if (inputResults.error != null) {
            return AlertError(Instant.now(), "Error fetching inputs:\n${inputResults.error.userErrorMessage()}")
        }
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
                .field("error", error?.message)
                .endObject()
    }
}

data class TriggerRunResult(
    val triggerName: String,
    val triggered: Boolean,
    val error: Exception? = null,
    val actionResults: MutableMap<String, ActionRunResult> = mutableMapOf()
) : ToXContent {
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        var msg = error?.message
        if (error is ScriptException) msg = error.toJsonString()
        return builder.startObject()
                .field("name", triggerName)
                .field("triggered", triggered)
                .field("error", msg)
                .field("action_results", actionResults as Map<String, ActionRunResult>)
                .endObject()
    }

    /** Returns error information to store in the Alert. Currently it's just the stack trace but it can be more */
    fun alertError(): AlertError? {
        if (error != null) {
            return AlertError(Instant.now(), "Error evaluating trigger:\n${error.userErrorMessage()}")
        }
        for (actionResult in actionResults.values) {
            if (actionResult.error != null) {
                return AlertError(Instant.now(), "Error running action:\n${actionResult.error.userErrorMessage()}")
            }
        }
        return null
    }
}

data class ActionRunResult(
    val actionId: String,
    val actionName: String,
    val output: Map<String, String>,
    val throttled: Boolean = false,
    val executionTime: Instant? = null,
    val error: Exception? = null
) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("id", actionId)
                .field("name", actionName)
                .field("output", output)
                .field("throttled", throttled)
                .optionalTimeField("executionTime", executionTime)
                .field("error", error?.message)
                .endObject()
    }
}

private val logger = LogManager.getLogger(MonitorRunResult::class.java)

/** Constructs an error message from an exception suitable for human consumption. */
private fun Throwable.userErrorMessage(): String {
    return when {
        this is ScriptException -> this.scriptStack.joinToString(separator = "\n", limit = 100)
        this is ElasticsearchException -> this.detailedMessage
        this.message != null -> {
            logger.info("Internal error: ${this.message}. See the Elasticsearch.log for details", this)
            this.message!!
        }
        else -> {
            logger.info("Unknown Internal error. See the Elasticsearch log for details.", this)
            "Unknown Internal error. See the Elasticsearch log for details."
        }
    }
}
