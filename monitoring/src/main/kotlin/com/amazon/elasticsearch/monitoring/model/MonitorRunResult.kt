/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.model

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import java.time.Instant

class MonitorRunResult(val monitorName: String, val periodStart: Instant, val periodEnd: Instant,
                       val triggerResults : MutableMap<String, TriggerRunResult> = mutableMapOf()) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("monitor_name", monitorName)
                .dateField("period_start_in_millis", "period_start", periodStart.toEpochMilli())
                .dateField("period_end_in_mills", "period_end", periodEnd.toEpochMilli())
                .field("trigger_results", triggerResults as Map<String, TriggerRunResult>)
                .endObject()
    }
}

class TriggerRunResult(val triggerName: String, val triggered: Boolean, val errorMessage: String? = null,
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

class ActionRunResult(val actionName: String, val output: String? = null, val throttled: Boolean = false,
                      val errorMessage: String? = null) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .field("output", output)
                .field("throttled", throttled)
                .field("error", errorMessage)
                .endObject()
    }
}