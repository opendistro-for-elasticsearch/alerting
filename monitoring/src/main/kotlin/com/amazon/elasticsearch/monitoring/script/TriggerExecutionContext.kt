/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.script

import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.Trigger
import java.time.Instant

data class TriggerExecutionContext(val monitor: Monitor, val trigger: Trigger,
                                   val results: List<Map<String, Any>>, val periodStart: Instant,
                                   val periodEnd: Instant, val alert: Alert? = null, val error: Exception? = null) {

    /**
     * Mustache templates need special permissions to reflectively introspect field names. To avoid doing this we
     * translate the context to a Map of Strings to primitive types, which can be accessed without reflection.
     */
    fun asTemplateArg() : Map<String, Any?> {
        return mapOf("monitor" to monitor.asTemplateArg(),
                "trigger" to trigger.asTemplateArg(),
                "results" to results,
                "periodStart" to periodStart,
                "periodEnd" to periodEnd,
                "alert" to alert?.asTemplateArg(),
                "error" to error)
    }
}
