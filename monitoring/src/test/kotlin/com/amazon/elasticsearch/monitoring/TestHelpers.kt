/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.Action
import com.amazon.elasticsearch.model.Input
import com.amazon.elasticsearch.model.IntervalSchedule
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.Schedule
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.alerts.AlertIndices
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.Trigger
import com.amazon.elasticsearch.util.string
import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.common.UUIDs
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
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
                  actions: List<Action> = (1..randomInt(10)).map { randomAction() }
                  ): Trigger {
    return Trigger(id = id, name = name, severity = severity, condition = condition, actions = actions)
}

fun randomScript(source: String = "return " + ESRestTestCase.randomBoolean().toString()) : Script = Script(source)

val ALWAYS_RUN = Script("return true")
val NEVER_RUN = Script("return false")

fun randomTemplateScript(source: String, params: Map<String, String> = emptyMap()) : Script =
        Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG, source, params)

fun randomAction(name: String = ESRestTestCase.randomUnicodeOfLength(10),
                 topicARN: String = "arn:aws:sns:foo:012345678901:bar",
                 roleARN: String = "arn:aws:iam::012345678901:foobar",
                 subjectTemplate: Script = randomTemplateScript("Hello World"),
                 messageTemplate: Script = randomTemplateScript("Goodbye World")) : Action {
    return SNSAction(name = name, topicARN = topicARN, roleARN = roleARN, messageTemplate = messageTemplate,
            subjectTemplate = subjectTemplate)
}

fun randomAlert(monitor: Monitor = randomMonitor()) : Alert {
    val trigger = randomTrigger()
    return Alert(monitor, trigger, Instant.now().truncatedTo(ChronoUnit.MILLIS), null)
}

fun putAlertMappings(client: RestClient) {
    client.performRequest("PUT", "/.aes-alerts")
    client.performRequest("PUT", "/.aes-alerts/_mapping/_doc", emptyMap(), StringEntity(AlertIndices.alertMapping(), ContentType.APPLICATION_JSON))
}

fun Response.restStatus() : RestStatus {
    return RestStatus.fromCode(this.statusLine.statusCode)
}

fun Monitor.toHttpEntity() : HttpEntity {
    return StringEntity(toJsonString(), ContentType.APPLICATION_JSON)
}

fun Monitor.toJsonString(): String {
    val builder = XContentFactory.jsonBuilder()
    return this.toXContent(builder).string()
}

fun Monitor.relativeUrl() = "_awses/monitors/$id"
