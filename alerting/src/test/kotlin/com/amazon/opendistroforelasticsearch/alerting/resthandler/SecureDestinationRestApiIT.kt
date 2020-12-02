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
import com.amazon.opendistroforelasticsearch.alerting.DESTINATION_BASE_URI
import com.amazon.opendistroforelasticsearch.alerting.makeRequest
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Chime
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Slack
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.client.ResponseException
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.junit.annotations.TestLogging
import java.time.Instant

@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class SecureDestinationRestApiIT : AlertingRestTestCase() {

    fun `test create destination with disable filter by`() {
        disableFilterBy()

        val chime = Chime("http://abc.com")
        val destination = Destination(
                type = DestinationType.CHIME,
                name = "test",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = chime,
                slack = null,
                customWebhook = null,
                email = null)
        val createdDestination = createDestination(destination = destination)
        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.CHIME)
    }

    fun `test create destination with enable filter by`() {
        enableFilterBy()
        val chime = Chime("http://abc.com")
        val destination = Destination(
                type = DestinationType.CHIME,
                name = "test",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = chime,
                slack = null,
                customWebhook = null,
                email = null)

        if (securityEnabled()) {
            // when security is enabled. No errors, must succeed.
            val response = client().makeRequest(
                    "POST",
                    "$DESTINATION_BASE_URI?refresh=true",
                    emptyMap(),
                    destination.toHttpEntity())
            assertEquals("Create monitor failed", RestStatus.CREATED, response.restStatus())
        } else {
            // when security is disable. Must return Forbidden.
            try {
                client().makeRequest(
                        "POST",
                        "$DESTINATION_BASE_URI?refresh=true",
                        emptyMap(),
                        destination.toHttpEntity())
                fail("Expected 403 FORBIDDEN response")
            } catch (e: ResponseException) {
                assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
            }
        }
    }

    fun `test update destination with disable filter by`() {
        disableFilterBy()

        val chime = Chime("http://abc.com")
        val destination = Destination(
            type = DestinationType.CHIME,
            name = "test",
            user = randomUser(),
            lastUpdateTime = Instant.now(),
            chime = chime,
            slack = null,
            customWebhook = null,
            email = null)
        val createdDestination = createDestination(destination = destination)

        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.CHIME)

        val slack = Slack("http://abc.com")
        val destinationV2 = createdDestination.copy(name = "testUpdate", type = DestinationType.SLACK, chime = null, slack = slack)

        val updatedDestination = updateDestination(destination = destinationV2)
        assertEquals("Incorrect destination name", updatedDestination.name, "testUpdate")
        assertEquals("Incorrect destination type", updatedDestination.type, DestinationType.SLACK)
    }

    fun `test update destination with enable filter by`() {
        enableFilterBy()
        if (!isHttps()) {
            // if security is disabled and filter by is enabled, we can't create monitor
            // refer: `test create destination with enable filter by`
            return
        }

        val chime = Chime("http://abc.com")
        val destination = Destination(
            type = DestinationType.CHIME,
            name = "test",
            user = randomUser(),
            lastUpdateTime = Instant.now(),
            chime = chime,
            slack = null,
            customWebhook = null,
            email = null)

        // 1. create a destination as admin user
        val createdDestination = createDestination(destination, true)

        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.CHIME)

        val slack = Slack("http://abc.com")
        val destinationV2 = createdDestination.copy(name = "testUpdate", type = DestinationType.SLACK, chime = null, slack = slack)

        val updatedDestination = updateDestination(destination = destinationV2)
        assertEquals("Incorrect destination name", updatedDestination.name, "testUpdate")
        assertEquals("Incorrect destination type", updatedDestination.type, DestinationType.SLACK)
    }

    fun `test delete destination with disable filter by`() {
        disableFilterBy()

        val chime = Chime("http://abc.com")
        val destination = Destination(
            type = DestinationType.CHIME,
            name = "test",
            user = randomUser(),
            lastUpdateTime = Instant.now(),
            chime = chime,
            slack = null,
            customWebhook = null,
            email = null)
        val createdDestination = createDestination(destination = destination)

        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.CHIME)

        deleteDestination(createdDestination)

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"
        inputMap["destinationType"] = "chime"

        // get destinations as admin user
        val adminResponse = getDestinations(client(), inputMap)
        assertEquals(0, adminResponse.size)
    }

    fun `test delete destination with enable filter by`() {
        enableFilterBy()
        if (!isHttps()) {
            // if security is disabled and filter by is enabled, we can't create monitor
            // refer: `test create destination with enable filter by`
            return
        }

        val chime = Chime("http://abc.com")
        val destination = Destination(
            type = DestinationType.CHIME,
            name = "test",
            user = randomUser(),
            lastUpdateTime = Instant.now(),
            chime = chime,
            slack = null,
            customWebhook = null,
            email = null)

        // 1. create a destination as admin user
        val createdDestination = createDestination(destination, true)

        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.CHIME)

        deleteDestination(createdDestination)

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"
        inputMap["destinationType"] = "chime"

        // 2. get destinations as admin user
        val adminResponse = getDestinations(client(), inputMap)
        assertEquals(0, adminResponse.size)
    }

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
        val adminResponse = getDestinations(client(), inputMap)
        assertEquals(1, adminResponse.size)
    }

    fun `test get destinations with a destination type and filter by`() {
        enableFilterBy()
        if (!securityEnabled()) {
            // if security is disabled and filter by is enabled, we can't create monitor
            // refer: `test create destination with enable filter by`
            return
        }
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
        val adminResponse = getDestinations(client(), inputMap)
        assertEquals(1, adminResponse.size)
    }
}
