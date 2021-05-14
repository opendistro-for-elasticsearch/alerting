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

package com.amazon.opendistroforelasticsearch.alerting

import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTrigger
import com.amazon.opendistroforelasticsearch.alerting.model.TraditionalTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.script.TraditionalTriggerExecutionContext
import com.amazon.opendistroforelasticsearch.alerting.script.TriggerScript
import org.apache.logging.log4j.LogManager
import org.elasticsearch.client.Client
import org.elasticsearch.script.ScriptService

/** Service that handles executing Triggers */
class TriggerService(val client: Client, val scriptService: ScriptService) {

    private val logger = LogManager.getLogger(TriggerService::class.java)

    fun isTraditionalTriggerActionable(ctx: TraditionalTriggerExecutionContext, result: TraditionalTriggerRunResult): Boolean {
        // Suppress actions if the current alert is acknowledged and there are no errors.
        val suppress = ctx.alert?.state == Alert.State.ACKNOWLEDGED && result.error == null && ctx.error == null
        return result.triggered && !suppress
    }

    fun runTraditionalTrigger(monitor: Monitor, trigger: TraditionalTrigger, ctx: TraditionalTriggerExecutionContext): TraditionalTriggerRunResult {
        return try {
            val triggered = scriptService.compile(trigger.condition, TriggerScript.CONTEXT)
                .newInstance(trigger.condition.params)
                .execute(ctx)
            TraditionalTriggerRunResult(trigger.name, triggered, null)
        } catch (e: Exception) {
            logger.info("Error running script for monitor ${monitor.id}, trigger: ${trigger.id}", e)
            // if the script fails we need to send an alert so set triggered = true
            TraditionalTriggerRunResult(trigger.name, true, e)
        }
    }
}
