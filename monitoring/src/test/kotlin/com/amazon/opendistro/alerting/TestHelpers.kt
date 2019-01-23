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
package com.amazon.opendistro.alerting

import com.amazon.opendistro.model.action.Action
import com.amazon.opendistro.model.Input
import com.amazon.opendistro.model.IntervalSchedule
import com.amazon.opendistro.model.SNSAction
import com.amazon.opendistro.model.Schedule
import com.amazon.opendistro.model.SearchInput
import com.amazon.opendistro.alerting.model.Alert
import com.amazon.opendistro.alerting.model.Monitor
import com.amazon.opendistro.alerting.model.TestAction
import com.amazon.opendistro.alerting.model.Trigger
import com.amazon.opendistro.util.string
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
        withMetadata: Boolean = false): Monitor {
    return Monitor(name = name, enabled = enabled, inputs = inputs, schedule = schedule, triggers = triggers,
            enabledTime = enabledTime, lastUpdateTime = lastUpdateTime,
            uiMetadata = if (withMetadata) mapOf("foo" to "bar") else mapOf())
}

fun randomTrigger(id: String = UUIDs.base64UUID(),
                  name: String = ESRestTestCase.randomAlphaOfLength(10),
                  severity: String = "1",
                  condition: Script = randomScript(),
                  actions: List<Action> = (0..randomInt(10)).map { randomAction() }
                  ): Trigger {
    return Trigger(id = id, name = name, severity = severity, condition = condition, actions = actions)
}

fun randomScript(source: String = "return " + ESRestTestCase.randomBoolean().toString()) : Script = Script(source)

val ALERTING_BASE_URI = "/_alerting/monitors"
val ALWAYS_RUN = Script("return true")
val NEVER_RUN = Script("return false")
val DRYRUN_MONITOR = mapOf("dryrun" to "true")

fun randomTemplateScript(source: String, params: Map<String, String> = emptyMap()) : Script =
        Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG, source, params)

fun randomSNSAction(name: String = ESRestTestCase.randomUnicodeOfLength(10),
                 topicARN: String = "arn:aws:sns:foo:012345678901:bar",
                 roleARN: String = "arn:aws:iam::012345678901:foobar",
                 subjectTemplate: Script = randomTemplateScript("Hello World"),
                 messageTemplate: Script = randomTemplateScript("Goodbye World")) : Action {
    return SNSAction(name = name, topicARN = topicARN, roleARN = roleARN, messageTemplate = messageTemplate,
            subjectTemplate = subjectTemplate)
}

fun randomAction(name: String = ESRestTestCase.randomUnicodeOfLength(10),
                 template: Script = randomTemplateScript("Hello World")) = TestAction(name, template)

fun randomAlert(monitor: Monitor = randomMonitor()) : Alert {
    val trigger = randomTrigger()
    return Alert(monitor, trigger, Instant.now().truncatedTo(ChronoUnit.MILLIS), null)
}

fun Monitor.toJsonString(): String {
    val builder = XContentFactory.jsonBuilder()
    return this.toXContent(builder).string()
}
