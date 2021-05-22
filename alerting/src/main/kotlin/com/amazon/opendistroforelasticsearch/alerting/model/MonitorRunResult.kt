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
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.script.ScriptException
import java.io.IOException
import java.time.Instant

data class MonitorRunResult<TriggerResult: TriggerRunResult>(
    val monitorName: String,
    val periodStart: Instant,
    val periodEnd: Instant,
    val error: Exception? = null,
    val inputResults: InputRunResults = InputRunResults(),
    val triggerResults: Map<String, TriggerResult> = mapOf()
) : Writeable, ToXContent {

    @Throws(IOException::class)
    @Suppress("UNCHECKED_CAST")
    constructor(sin: StreamInput): this(
        sin.readString(), // monitorName
        sin.readInstant(), // periodStart
        sin.readInstant(), // periodEnd
        sin.readException(), // error
        InputRunResults.readFrom(sin), // inputResults
        suppressWarning(sin.readMap()) as Map<String, TriggerResult> // triggerResults
    )

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
            return AlertError(Instant.now(), "Failed running monitor:\n${error.userErrorMessage()}")
        }

        if (inputResults.error != null) {
            return AlertError(Instant.now(), "Failed fetching inputs:\n${inputResults.error.userErrorMessage()}")
        }
        return null
    }

    fun scriptContextError(trigger: Trigger): Exception? {
        return error ?: inputResults.error ?: triggerResults[trigger.id]?.error
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): MonitorRunResult<TriggerRunResult> {
            return MonitorRunResult(sin)
        }

        @Suppress("UNCHECKED_CAST")
        fun suppressWarning(map: MutableMap<String?, Any?>?): Map<String, TriggerRunResult> {
            return map as Map<String, TriggerRunResult>
        }
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(monitorName)
        out.writeInstant(periodStart)
        out.writeInstant(periodEnd)
        out.writeException(error)
        inputResults.writeTo(out)
        out.writeMap(triggerResults)
    }
}

data class InputRunResults(val results: List<Map<String, Any>> = listOf(), val error: Exception? = null) : Writeable, ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("results", results)
                .field("error", error?.message)
                .endObject()
    }
    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeVInt(results.size)
        for (map in results) {
            out.writeMap(map)
        }
        out.writeException(error)
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): InputRunResults {
            val count = sin.readVInt() // count
            val list = mutableListOf<Map<String, Any>>()
            for (i in 0 until count) {
                list.add(suppressWarning(sin.readMap())) // result(map)
            }
            val error = sin.readException<Exception>() // error
            return InputRunResults(list, error)
        }

        @Suppress("UNCHECKED_CAST")
        fun suppressWarning(map: MutableMap<String?, Any?>?): Map<String, Any> {
            return map as Map<String, Any>
        }
    }
}

data class ActionRunResult(
    val actionId: String,
    val actionName: String,
    val output: Map<String, String>,
    val throttled: Boolean = false,
    val executionTime: Instant? = null,
    val error: Exception? = null
) : Writeable, ToXContent {

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readString(), // actionId
        sin.readString(), // actionName
        suppressWarning(sin.readMap()), // output
        sin.readBoolean(), // throttled
        sin.readOptionalInstant(), // executionTime
        sin.readException() // error
    )

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

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(actionId)
        out.writeString(actionName)
        out.writeMap(output)
        out.writeBoolean(throttled)
        out.writeOptionalInstant(executionTime)
        out.writeException(error)
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): ActionRunResult {
            return ActionRunResult(sin)
        }

        @Suppress("UNCHECKED_CAST")
        fun suppressWarning(map: MutableMap<String?, Any?>?): MutableMap<String, String> {
            return map as MutableMap<String, String>
        }
    }
}

private val logger = LogManager.getLogger(MonitorRunResult::class.java)

/** Constructs an error message from an exception suitable for human consumption. */
fun Throwable.userErrorMessage(): String {
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
