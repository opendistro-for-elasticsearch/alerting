/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.util.optionalTimeField
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import java.time.Instant

data class MonitorRunResult(val monitorName: String, val periodStart: Instant, val periodEnd: Instant,
                            val errorMessage: String? = null,
                            val triggerResults : Map<String, TriggerRunResult> = mapOf()) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("monitor_name", monitorName)
                .optionalTimeField("period_start", periodStart)
                .optionalTimeField("period_end", periodEnd)
                .field("error", errorMessage)
                .field("trigger_results", triggerResults)
                .endObject()
    }
}

data class TriggerRunResult(val triggerName: String, val triggered: Boolean, val errorMessage: String? = null,
                            val actionResults : MutableMap<String, ActionRunResult> = mutableMapOf()) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("name", triggerName)
                .field("triggered", triggered)
                .field("error", errorMessage)
                .field("action_results", actionResults as Map<String, ActionRunResult>)
                .endObject()
    }
}

data class ActionRunResult(val actionName: String, val output: Map<String, String>, val throttled: Boolean = false,
                           val errorMessage: String? = null) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("name", actionName)
                .field("output", output)
                .field("throttled", throttled)
                .field("error", errorMessage)
                .endObject()
    }
}