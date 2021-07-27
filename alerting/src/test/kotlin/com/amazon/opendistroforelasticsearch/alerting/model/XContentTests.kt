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

import com.amazon.opendistroforelasticsearch.alerting.builder
import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action
import com.amazon.opendistroforelasticsearch.alerting.model.action.ActionExecutionPolicy
import com.amazon.opendistroforelasticsearch.alerting.model.action.Throttle
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import com.amazon.opendistroforelasticsearch.alerting.parser
import com.amazon.opendistroforelasticsearch.alerting.randomAction
import com.amazon.opendistroforelasticsearch.alerting.randomActionExecutionPolicy
import com.amazon.opendistroforelasticsearch.alerting.randomActionExecutionResult
import com.amazon.opendistroforelasticsearch.alerting.randomBucketLevelMonitor
import com.amazon.opendistroforelasticsearch.alerting.randomBucketLevelTrigger
import com.amazon.opendistroforelasticsearch.alerting.randomAlert
import com.amazon.opendistroforelasticsearch.alerting.randomEmailAccount
import com.amazon.opendistroforelasticsearch.alerting.randomEmailGroup
import com.amazon.opendistroforelasticsearch.alerting.randomQueryLevelMonitor
import com.amazon.opendistroforelasticsearch.alerting.randomQueryLevelMonitorWithoutUser
import com.amazon.opendistroforelasticsearch.alerting.randomThrottle
import com.amazon.opendistroforelasticsearch.alerting.randomQueryLevelTrigger
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.randomUserEmpty
import com.amazon.opendistroforelasticsearch.alerting.toJsonString
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import kotlin.test.assertFailsWith

class XContentTests : ESTestCase() {

    fun `test action parsing`() {
        val action = randomAction()
        val actionString = action.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedAction = Action.parse(parser(actionString))
        assertEquals("Round tripping Action doesn't work", action, parsedAction)
    }

    fun `test action parsing with null subject template`() {
        val action = randomAction().copy(subjectTemplate = null)
        val actionString = action.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedAction = Action.parse(parser(actionString))
        assertEquals("Round tripping Action doesn't work", action, parsedAction)
    }

    fun `test action parsing with null throttle`() {
        val action = randomAction().copy(throttle = null)
        val actionString = action.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedAction = Action.parse(parser(actionString))
        assertEquals("Round tripping Action doesn't work", action, parsedAction)
    }

    fun `test action parsing with throttled enabled and null throttle`() {
        val action = randomAction().copy(throttle = null).copy(throttleEnabled = true)
        val actionString = action.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        assertFailsWith<IllegalArgumentException>("Action throttle enabled but not set throttle value") {
            Action.parse(parser(actionString)) }
    }

    fun `test throttle parsing`() {
        val throttle = randomThrottle()
        val throttleString = throttle.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedThrottle = Throttle.parse(parser(throttleString))
        assertEquals("Round tripping Monitor doesn't work", throttle, parsedThrottle)
    }

    fun `test throttle parsing with wrong unit`() {
        val throttle = randomThrottle()
        val throttleString = throttle.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val wrongThrottleString = throttleString.replace("MINUTES", "wrongunit")

        assertFailsWith<IllegalArgumentException>("Only support MINUTES throttle unit") { Throttle.parse(parser(wrongThrottleString)) }
    }

    fun `test throttle parsing with negative value`() {
        val throttle = randomThrottle().copy(value = -1)
        val throttleString = throttle.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()

        assertFailsWith<IllegalArgumentException>("Can only set positive throttle period") { Throttle.parse(parser(throttleString)) }
    }

    fun `test query-level monitor parsing`() {
        val monitor = randomQueryLevelMonitor()

        val monitorString = monitor.toJsonString()
        val parsedMonitor = Monitor.parse(parser(monitorString))
        assertEquals("Round tripping QueryLevelMonitor doesn't work", monitor, parsedMonitor)
    }

    fun `test query-level trigger parsing`() {
        val trigger = randomQueryLevelTrigger()

        val triggerString = trigger.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedTrigger = Trigger.parse(parser(triggerString))

        assertEquals("Round tripping QueryLevelTrigger doesn't work", trigger, parsedTrigger)
    }

    fun `test bucket-level trigger parsing`() {
        val trigger = randomBucketLevelTrigger()

        val triggerString = trigger.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedTrigger = Trigger.parse(parser(triggerString))

        assertEquals("Round tripping BucketLevelTrigger doesn't work", trigger, parsedTrigger)
    }

    fun `test alert parsing`() {
        val alert = randomAlert()

        val alertString = alert.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedAlert = Alert.parse(parser(alertString))

        assertEquals("Round tripping alert doesn't work", alert, parsedAlert)
    }

    fun `test alert parsing without user`() {
        val alertStr = "{\"id\":\"\",\"version\":-1,\"monitor_id\":\"\",\"schema_version\":0,\"monitor_version\":1," +
                "\"monitor_name\":\"ARahqfRaJG\",\"trigger_id\":\"fhe1-XQBySl0wQKDBkOG\",\"trigger_name\":\"ffELMuhlro\"," +
                "\"state\":\"ACTIVE\",\"error_message\":null,\"alert_history\":[],\"severity\":\"1\",\"action_execution_results\"" +
                ":[{\"action_id\":\"ghe1-XQBySl0wQKDBkOG\",\"last_execution_time\":1601917224583,\"throttled_count\":-1478015168}," +
                "{\"action_id\":\"gxe1-XQBySl0wQKDBkOH\",\"last_execution_time\":1601917224583,\"throttled_count\":-768533744}]," +
                "\"start_time\":1601917224599,\"last_notification_time\":null,\"end_time\":null,\"acknowledged_time\":null}"
        val parsedAlert = Alert.parse(parser(alertStr))
        assertNull(parsedAlert.monitorUser)
    }

    fun `test alert parsing with user as null`() {
        val alertStr = "{\"id\":\"\",\"version\":-1,\"monitor_id\":\"\",\"schema_version\":0,\"monitor_version\":1,\"monitor_user\":null," +
            "\"monitor_name\":\"ARahqfRaJG\",\"trigger_id\":\"fhe1-XQBySl0wQKDBkOG\",\"trigger_name\":\"ffELMuhlro\"," +
            "\"state\":\"ACTIVE\",\"error_message\":null,\"alert_history\":[],\"severity\":\"1\",\"action_execution_results\"" +
            ":[{\"action_id\":\"ghe1-XQBySl0wQKDBkOG\",\"last_execution_time\":1601917224583,\"throttled_count\":-1478015168}," +
            "{\"action_id\":\"gxe1-XQBySl0wQKDBkOH\",\"last_execution_time\":1601917224583,\"throttled_count\":-768533744}]," +
            "\"start_time\":1601917224599,\"last_notification_time\":null,\"end_time\":null,\"acknowledged_time\":null}"
        val parsedAlert = Alert.parse(parser(alertStr))
        assertNull(parsedAlert.monitorUser)
    }

    fun `test action execution result parsing`() {
        val actionExecutionResult = randomActionExecutionResult()

        val actionExecutionResultString = actionExecutionResult.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedActionExecutionResultString = ActionExecutionResult.parse(parser(actionExecutionResultString))

        assertEquals("Round tripping alert doesn't work", actionExecutionResult, parsedActionExecutionResultString)
    }

    fun `test creating a monitor with duplicate trigger ids fails`() {
        try {
            val repeatedTrigger = randomQueryLevelTrigger()
            randomQueryLevelMonitor().copy(triggers = listOf(repeatedTrigger, repeatedTrigger))
            fail("Creating a monitor with duplicate triggers did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test user parsing`() {
        val user = randomUser()
        val userString = user.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedUser = User.parse(parser(userString))
        assertEquals("Round tripping user doesn't work", user, parsedUser)
    }

    fun `test empty user parsing`() {
        val user = randomUserEmpty()
        val userString = user.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()

        val parsedUser = User.parse(parser(userString))
        assertEquals("Round tripping user doesn't work", user, parsedUser)
        assertEquals("", parsedUser.name)
        assertEquals(0, parsedUser.roles.size)
    }

    fun `test query-level monitor parsing without user`() {
        val monitor = randomQueryLevelMonitorWithoutUser()

        val monitorString = monitor.toJsonString()
        val parsedMonitor = Monitor.parse(parser(monitorString))
        assertEquals("Round tripping QueryLevelMonitor doesn't work", monitor, parsedMonitor)
        assertNull(parsedMonitor.user)
    }

    fun `test email account parsing`() {
        val emailAccount = randomEmailAccount()

        val emailAccountString = emailAccount.toJsonString()
        val parsedEmailAccount = EmailAccount.parse(parser(emailAccountString))
        assertEquals("Round tripping EmailAccount doesn't work", emailAccount, parsedEmailAccount)
    }

    fun `test email group parsing`() {
        val emailGroup = randomEmailGroup()

        val emailGroupString = emailGroup.toJsonString()
        val parsedEmailGroup = EmailGroup.parse(parser(emailGroupString))
        assertEquals("Round tripping EmailGroup doesn't work", emailGroup, parsedEmailGroup)
    }

    fun `test old monitor format parsing`() {
        val monitorString = """
            {
              "type": "monitor",
              "schema_version": 3,
              "name": "asdf",
              "user": {
                "name": "admin123",
                "backend_roles": [],
                "roles": [
                  "all_access",
                  "security_manager"
                ],
                "custom_attribute_names": [],
                "user_requested_tenant": null
              },
              "enabled": true,
              "enabled_time": 1613530078244,
              "schedule": {
                "period": {
                  "interval": 1,
                  "unit": "MINUTES"
                }
              },
              "inputs": [
                {
                  "search": {
                    "indices": [
                      "test_index"
                    ],
                    "query": {
                      "size": 0,
                      "query": {
                        "bool": {
                          "filter": [
                            {
                              "range": {
                                "order_date": {
                                  "from": "{{period_end}}||-1h",
                                  "to": "{{period_end}}",
                                  "include_lower": true,
                                  "include_upper": true,
                                  "format": "epoch_millis",
                                  "boost": 1.0
                                }
                              }
                            }
                          ],
                          "adjust_pure_negative": true,
                          "boost": 1.0
                        }
                      },
                      "aggregations": {}
                    }
                  }
                }
              ],
              "triggers": [
                {
                  "id": "e_sc0XcB98Q42rHjTh4K",
                  "name": "abc",
                  "severity": "1",
                  "condition": {
                    "script": {
                      "source": "ctx.results[0].hits.total.value > 100000",
                      "lang": "painless"
                    }
                  },
                  "actions": []
                }
              ],
              "last_update_time": 1614121489719
            }
        """.trimIndent()
        val parsedMonitor = Monitor.parse(parser(monitorString))
        assertEquals("Incorrect monitor type", Monitor.MonitorType.QUERY_LEVEL_MONITOR, parsedMonitor.monitorType)
        assertEquals("Incorrect trigger count", 1, parsedMonitor.triggers.size)
        val trigger = parsedMonitor.triggers.first()
        assertTrue("Incorrect trigger type", trigger is QueryLevelTrigger)
        assertEquals("Incorrect name for parsed trigger", "abc", trigger.name)
    }

    fun `test creating an query-level monitor with invalid trigger type fails`() {
        try {
            val bucketLevelTrigger = randomBucketLevelTrigger()
            randomQueryLevelMonitor().copy(triggers = listOf(bucketLevelTrigger))
            fail("Creating a query-level monitor with bucket-level triggers did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test creating an bucket-level monitor with invalid trigger type fails`() {
        try {
            val queryLevelTrigger = randomQueryLevelTrigger()
            randomBucketLevelMonitor().copy(triggers = listOf(queryLevelTrigger))
            fail("Creating a bucket-level monitor with query-level triggers did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test creating an bucket-level monitor with invalid input fails`() {
        try {
            val invalidInput = SearchInput(emptyList(), SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
            randomBucketLevelMonitor().copy(inputs = listOf(invalidInput))
            fail("Creating an bucket-level monitor with an invalid input did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test action execution policy`() {
        val actionExecutionPolicy = randomActionExecutionPolicy()
        val actionExecutionPolicyString = actionExecutionPolicy.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedActionExecutionPolicy = ActionExecutionPolicy.parse(parser(actionExecutionPolicyString))
        assertEquals("Round tripping ActionExecutionPolicy doesn't work", actionExecutionPolicy, parsedActionExecutionPolicy)
    }

    fun `test action execution policy with null throttle`() {
        val actionExecutionPolicy = randomActionExecutionPolicy().copy(throttle = null)
        val actionExecutionPolicyString = actionExecutionPolicy.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val parsedActionExecutionPolicy = ActionExecutionPolicy.parse(parser(actionExecutionPolicyString))
        assertEquals("Round tripping ActionExecutionPolicy doesn't work", actionExecutionPolicy, parsedActionExecutionPolicy)
    }
}
