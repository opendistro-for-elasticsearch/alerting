/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.Action
import com.amazon.elasticsearch.model.IntervalSchedule
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Alert
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.Trigger
import com.amazon.elasticsearch.util.string
import org.elasticsearch.client.RestClient
import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.elasticsearch.client.Response
import org.elasticsearch.common.UUIDs
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptType
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.rest.ESRestTestCase
import java.time.Instant
import java.time.temporal.ChronoUnit

fun randomMonitor(withMetadata: Boolean = false): Monitor {
    return Monitor(name = ESRestTestCase.randomAlphaOfLength(10),
            enabled = ESTestCase.randomBoolean(),
            inputs = listOf(SearchInput(emptyList(), SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))),
            schedule = IntervalSchedule(interval = 5, unit = ChronoUnit.MINUTES),
            triggers = (1..ESTestCase.randomInt(10)).map { randomTrigger() },
            uiMetadata = if (withMetadata) mapOf("foo" to "bar") else mapOf(),
            enabledTime = null)
}

fun randomTrigger(): Trigger {
    return Trigger(id = UUIDs.base64UUID(),
            name = ESRestTestCase.randomAlphaOfLength(10),
            severity = "1",
            condition = randomScript(),
            actions = listOf(randomAction())
    )
}

fun randomScript() : Script {
    return Script("return true")
}

fun randomAction() : Action {
    return SNSAction(name = ESRestTestCase.randomUnicodeOfLength(10),
            topicARN = "arn:bar:baz",
            messageTemplate = Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG, "Goodbye {{_ctx.monitor.name}}!", emptyMap()),
            subjectTemplate = Script(ScriptType.INLINE, Script.DEFAULT_TEMPLATE_LANG, "Hello {{_ctx.monitor.name}}!", emptyMap()))
}

fun randomAlert(monitor: Monitor = randomMonitor()) : Alert {
    val trigger = randomTrigger()
    return Alert(monitor, trigger, Instant.now().truncatedTo(ChronoUnit.MILLIS), null)
}

// Temporary function until we have mappings in resource folder.
fun putAlertMappings(client: RestClient) {
    var mapping = """
        {"_doc": { "dynamic" : "strict", "properties" : { "monitor_id" : { "type" : "keyword" }, "monitor_version" : { "type" : "long" }, "severity": { "type": "keyword" }, "monitor_name" : { "type" : "text", "fields": { "keyword": { "type": "keyword", "ignore_above" : 256 } } }, "trigger_id" : { "type" : "keyword" }, "trigger_name" : { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above" : 256 } } }, "state" : { "type": "keyword" }, "start_time" : { "type" : "date" }, "last_notification_time" : { "type" : "date" }, "acknowledged_time" : { "type" : "date" },"end_time" : {"type" : "date"},"error_message" : {"type" : "text"}}}}
        """.trimIndent()
    client.performRequest("PUT", "/.aes-alerts")
    client.performRequest("PUT", "/.aes-alerts/_mapping/_doc", emptyMap(), StringEntity(mapping, ContentType.APPLICATION_JSON))
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
