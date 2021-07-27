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

package com.amazon.opendistroforelasticsearch.alerting.model

import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action
import com.amazon.opendistroforelasticsearch.alerting.model.action.ActionExecutionPolicy
import com.amazon.opendistroforelasticsearch.alerting.model.action.Throttle
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import com.amazon.opendistroforelasticsearch.alerting.randomAction
import com.amazon.opendistroforelasticsearch.alerting.randomActionExecutionPolicy
import com.amazon.opendistroforelasticsearch.alerting.randomActionRunResult
import com.amazon.opendistroforelasticsearch.alerting.randomBucketLevelTrigger
import com.amazon.opendistroforelasticsearch.alerting.randomBucketLevelMonitorRunResult
import com.amazon.opendistroforelasticsearch.alerting.randomBucketLevelTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.randomEmailAccount
import com.amazon.opendistroforelasticsearch.alerting.randomEmailGroup
import com.amazon.opendistroforelasticsearch.alerting.randomInputRunResults
import com.amazon.opendistroforelasticsearch.alerting.randomQueryLevelMonitor
import com.amazon.opendistroforelasticsearch.alerting.randomThrottle
import com.amazon.opendistroforelasticsearch.alerting.randomQueryLevelMonitorRunResult
import com.amazon.opendistroforelasticsearch.alerting.randomQueryLevelTrigger
import com.amazon.opendistroforelasticsearch.alerting.randomQueryLevelTriggerRunResult
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.randomUserEmpty
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase

class WriteableTests : ESTestCase() {

    fun `test throttle as stream`() {
        val throttle = randomThrottle()
        val out = BytesStreamOutput()
        throttle.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newThrottle = Throttle(sin)
        assertEquals("Round tripping Throttle doesn't work", throttle, newThrottle)
    }

    fun `test action as stream`() {
        val action = randomAction()
        val out = BytesStreamOutput()
        action.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newAction = Action(sin)
        assertEquals("Round tripping Action doesn't work", action, newAction)
    }

    fun `test action as stream with null subject template`() {
        val action = randomAction().copy(subjectTemplate = null)
        val out = BytesStreamOutput()
        action.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newAction = Action(sin)
        assertEquals("Round tripping Action doesn't work", action, newAction)
    }

    fun `test action as stream with null throttle`() {
        val action = randomAction().copy(throttle = null)
        val out = BytesStreamOutput()
        action.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newAction = Action(sin)
        assertEquals("Round tripping Action doesn't work", action, newAction)
    }

    fun `test action as stream with throttled enabled and null throttle`() {
        val action = randomAction().copy(throttle = null).copy(throttleEnabled = true)
        val out = BytesStreamOutput()
        action.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newAction = Action(sin)
        assertEquals("Round tripping Action doesn't work", action, newAction)
    }

    fun `test query-level monitor as stream`() {
        val monitor = randomQueryLevelMonitor().copy(inputs = listOf(SearchInput(emptyList(), SearchSourceBuilder())))
        val out = BytesStreamOutput()
        monitor.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newMonitor = Monitor(sin)
        assertEquals("Round tripping QueryLevelMonitor doesn't work", monitor, newMonitor)
    }

    fun `test query-level trigger as stream`() {
        val trigger = randomQueryLevelTrigger()
        val out = BytesStreamOutput()
        trigger.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newTrigger = QueryLevelTrigger.readFrom(sin)
        assertEquals("Round tripping QueryLevelTrigger doesn't work", trigger, newTrigger)
    }

    fun `test bucket-level trigger as stream`() {
        val trigger = randomBucketLevelTrigger()
        val out = BytesStreamOutput()
        trigger.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newTrigger = BucketLevelTrigger.readFrom(sin)
        assertEquals("Round tripping BucketLevelTrigger doesn't work", trigger, newTrigger)
    }

    fun `test actionrunresult as stream`() {
        val actionRunResult = randomActionRunResult()
        val out = BytesStreamOutput()
        actionRunResult.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newActionRunResult = ActionRunResult(sin)
        assertEquals("Round tripping ActionRunResult doesn't work", actionRunResult, newActionRunResult)
    }

    fun `test query-level triggerrunresult as stream`() {
        val runResult = randomQueryLevelTriggerRunResult()
        val out = BytesStreamOutput()
        runResult.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newRunResult = QueryLevelTriggerRunResult(sin)
        assertEquals("Round tripping ActionRunResult doesn't work", runResult, newRunResult)
    }

    fun `test bucket-level triggerrunresult as stream`() {
        val runResult = randomBucketLevelTriggerRunResult()
        val out = BytesStreamOutput()
        runResult.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newRunResult = BucketLevelTriggerRunResult(sin)
        assertEquals("Round tripping ActionRunResult doesn't work", runResult, newRunResult)
    }

    fun `test inputrunresult as stream`() {
        val runResult = randomInputRunResults()
        val out = BytesStreamOutput()
        runResult.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newRunResult = InputRunResults.readFrom(sin)
        assertEquals("Round tripping InputRunResults doesn't work", runResult, newRunResult)
    }

    fun `test query-level monitorrunresult as stream`() {
        val runResult = randomQueryLevelMonitorRunResult()
        val out = BytesStreamOutput()
        runResult.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newRunResult = MonitorRunResult<QueryLevelTriggerRunResult>(sin)
        assertEquals("Round tripping MonitorRunResult doesn't work", runResult, newRunResult)
    }

    fun `test bucket-level monitorrunresult as stream`() {
        val runResult = randomBucketLevelMonitorRunResult()
        val out = BytesStreamOutput()
        runResult.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newRunResult = MonitorRunResult<BucketLevelTriggerRunResult>(sin)
        assertEquals("Round tripping MonitorRunResult doesn't work", runResult, newRunResult)
    }

    fun `test searchinput as stream`() {
        val input = SearchInput(emptyList(), SearchSourceBuilder())
        val out = BytesStreamOutput()
        input.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newInput = SearchInput(sin)
        assertEquals("Round tripping MonitorRunResult doesn't work", input, newInput)
    }

    fun `test user as stream`() {
        val user = randomUser()
        val out = BytesStreamOutput()
        user.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newUser = User(sin)
        assertEquals("Round tripping User doesn't work", user, newUser)
    }

    fun `test empty user as stream`() {
        val user = randomUserEmpty()
        val out = BytesStreamOutput()
        user.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newUser = User(sin)
        assertEquals("Round tripping User doesn't work", user, newUser)
    }

    fun `test emailaccount as stream`() {
        val emailAccount = randomEmailAccount()
        val out = BytesStreamOutput()
        emailAccount.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newEmailAccount = EmailAccount.readFrom(sin)
        assertEquals("Round tripping EmailAccount doesn't work", emailAccount, newEmailAccount)
    }

    fun `test emailgroup as stream`() {
        val emailGroup = randomEmailGroup()
        val out = BytesStreamOutput()
        emailGroup.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newEmailGroup = EmailGroup.readFrom(sin)
        assertEquals("Round tripping EmailGroup doesn't work", emailGroup, newEmailGroup)
    }

    fun `test action execution policy as stream`() {
        val actionExecutionPolicy = randomActionExecutionPolicy()
        val out = BytesStreamOutput()
        actionExecutionPolicy.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newActionExecutionPolicy = ActionExecutionPolicy.readFrom(sin)
        assertEquals("Round tripping ActionExecutionPolicy doesn't work", actionExecutionPolicy, newActionExecutionPolicy)
    }
}
