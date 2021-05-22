/*
 *   Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.script

import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.MonitorRunResult
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTriggerRunResult
import java.time.Instant

data class TraditionalTriggerExecutionContext(
    override val monitor: Monitor,
    val trigger: TraditionalTrigger,
    override val results: List<Map<String, Any>>,
    override val periodStart: Instant,
    override val periodEnd: Instant,
    val alert: Alert? = null,
    override val error: Exception? = null
) : TriggerExecutionContext(monitor, results, periodStart, periodEnd, error) {

    constructor(monitor: Monitor, trigger: TraditionalTrigger, monitorRunResult: MonitorRunResult<TraditionalTriggerRunResult>,
                alert: Alert? = null):
            this(monitor, trigger, monitorRunResult.inputResults.results, monitorRunResult.periodStart,
            monitorRunResult.periodEnd, alert, monitorRunResult.scriptContextError(trigger))

    /**
     * Mustache templates need special permissions to reflectively introspect field names. To avoid doing this we
     * translate the context to a Map of Strings to primitive types, which can be accessed without reflection.
     */
    override fun asTemplateArg(): Map<String, Any?> {
        val tempArg = super.asTemplateArg().toMutableMap()
        tempArg["trigger"] = trigger.asTemplateArg()
        tempArg["alert"] = alert?.asTemplateArg()
        return tempArg
    }
}
