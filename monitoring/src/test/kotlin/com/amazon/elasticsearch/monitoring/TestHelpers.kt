/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring

import com.amazon.elasticsearch.model.Action
import com.amazon.elasticsearch.model.IntervalSchedule
import com.amazon.elasticsearch.model.SNSAction
import com.amazon.elasticsearch.model.SearchInput
import com.amazon.elasticsearch.monitoring.model.Monitor
import com.amazon.elasticsearch.monitoring.model.Trigger
import org.elasticsearch.common.UUIDs
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.rest.ESRestTestCase
import java.time.temporal.ChronoUnit

fun randomMonitor(withMetadata: Boolean = false): Monitor {
    return Monitor(name = ESRestTestCase.randomAlphaOfLength(10),
            enabled = ESTestCase.randomBoolean(),
            inputs = listOf(SearchInput(emptyList(), SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))),
            schedule = IntervalSchedule(interval = 5, unit = ChronoUnit.MINUTES),
            triggers = (1..ESTestCase.randomInt(10)).map { randomTrigger() },
            uiMetadata = if (withMetadata) mapOf("foo" to "bar") else mapOf())
}

fun randomTrigger(): Trigger {
    return Trigger(id = UUIDs.base64UUID(),
            name = "foo",
            severity = 1,
            condition = randomScript(),
            actions = listOf(randomAction())
    )
}

fun randomScript() : Script {
    return Script("return true")
}

fun randomAction() : Action {
    return SNSAction(name = "foo",
            topicARN = "arn:bar:baz",
            messageTemplate = "quick brown fox",
            subjectTemplate = "you know the rest")
}

