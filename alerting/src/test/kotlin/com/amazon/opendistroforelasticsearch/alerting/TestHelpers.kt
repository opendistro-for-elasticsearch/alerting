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
package com.amazon.opendistroforelasticsearch.alerting

import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action
import com.amazon.opendistroforelasticsearch.alerting.core.model.Input
import com.amazon.opendistroforelasticsearch.alerting.core.model.IntervalSchedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.Schedule
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import org.elasticsearch.common.UUIDs
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptType
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.ESTestCase.randomInt
import org.elasticsearch.test.rest.ESRestTestCase
import java.time.Instant
import java.time.temporal.ChronoUnit

fun randomMonitor(
    name: String = ESRestTestCase.randomAlphaOfLength(10),
    inputs: List<Input> = listOf(SearchInput(emptyList(), SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))),
    schedule: Schedule = IntervalSchedule(interval = 5, unit = ChronoUnit.MINUTES),
    enabled: Boolean = ESTestCase.randomBoolean(),
    triggers: List<Trigger> = (1..randomInt(10)).map { randomTrigger() },
    enabledTime: Instant? = if (enabled) Instant.now().truncatedTo(ChronoUnit.MILLIS) else null,
    lastUpdateTime: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    withMetadata: Boolean = false
): Monitor {
    return Monitor(name = name, enabled = enabled, inputs = inputs, schedule = schedule, triggers = triggers,
            enabledTime = enabledTime, lastUpdateTime = lastUpdateTime,
            uiMetadata = if (withMetadata) mapOf("foo" to "bar") else mapOf())
}

fun randomTrigger(
    id: String = UUIDs.base64UUID(),
    name: String = ESRestTestCase.randomAlphaOfLength(10),
    severity: String = "1",
    condition: Script = randomScript(),
    actions: List<Action> = mutableListOf(),
    destinationId: String = ""
): Trigger {
    return Trigger(
        id = id,
        name = name,
        severity = severity,
        condition = condition,
        actions = if (actions.isEmpty()) (0..randomInt(10)).map { randomAction(destinationId = destinationId) } else actions)
}

fun randomScript(source: String = "return " + ESRestTestCase.randomBoolean().toString()): Script = Script(source)

val ALERTING_BASE_URI = "/_opendistro/_alerting/monitors"
val DESTINATION_BASE_URI = "/_opendistro/_alerting/destinations"
val ALWAYS_RUN = Script("return true")
val NEVER_RUN = Script("return false")
val DRYRUN_MONITOR = mapOf("dryrun" to "true")

fun randomTemplateScript(
    source: String,
    params: Map<String, String> = emptyMap()
): Script = Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG, source, params)

fun randomAction(
    name: String = ESRestTestCase.randomUnicodeOfLength(10),
    template: Script = randomTemplateScript("Hello World"),
    destinationId: String = "123"
) = Action(name, destinationId, template, template)

fun randomAlert(monitor: Monitor = randomMonitor()): Alert {
    val trigger = randomTrigger()
    return Alert(monitor, trigger, Instant.now().truncatedTo(ChronoUnit.MILLIS), null)
}

fun Monitor.toJsonString(): String {
    val builder = XContentFactory.jsonBuilder()
    return this.toXContent(builder).string()
}
