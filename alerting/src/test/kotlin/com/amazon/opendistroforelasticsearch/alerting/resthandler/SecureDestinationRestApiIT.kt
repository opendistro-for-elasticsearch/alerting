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

package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Slack
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.test.junit.annotations.TestLogging
import java.time.Instant

@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class SecureDestinationRestApiIT : AlertingRestTestCase() {

    fun `test get destinations with a destination type and disable filter by`() {
        disableFilterBy()
        val slack = Slack("url")
        val destination = Destination(
                type = DestinationType.SLACK,
                name = "testSlack",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = slack,
                customWebhook = null,
                email = null)

        // 1. create a destination as admin user
        createDestination(destination, true)

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"
        inputMap["destinationType"] = "slack"

        // 2. get destinations as admin user
        /*val adminResponse = getDestinations(client(), inputMap, getHeader())
        assertEquals(1, adminResponse.size)*/

        // 3. get destinations as kirk user, super-admin can read all.
        val kirkResponse = getDestinations(adminClient(), inputMap)
        assertEquals(1, kirkResponse.size)
    }

    fun `test get destinations with a destination type and filter by`() {
        enableFilterBy()
        val slack = Slack("url")
        val destination = Destination(
                type = DestinationType.SLACK,
                name = "testSlack",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = slack,
                customWebhook = null,
                email = null)

        // 1. create a destination as admin user
        createDestination(destination, true)

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"
        inputMap["destinationType"] = "slack"

        // 2. get destinations as admin user
        /*val adminResponse = getDestinations(client(), inputMap, getHeader())
        val expected = when (isHttps()) {
            true -> 1   // when test is run with security - get the correct filtered results.
            false -> 1  // when test is run without security and filterby is enabled - filtering
                        // does not work without security, so filtering is ignored and gets a result
        }
        assertEquals(expected, adminResponse.size)*/

        // 3. get destinations as kirk user, super-admin can read all.
        val kirkResponse = getDestinations(adminClient(), inputMap)
        assertEquals(1, kirkResponse.size)
    }
}
