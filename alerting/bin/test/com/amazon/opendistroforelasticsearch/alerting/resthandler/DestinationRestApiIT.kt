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
import com.amazon.opendistroforelasticsearch.alerting.model.destination.CustomWebhook
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.Email
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.Recipient
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Slack
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.client.ResponseException
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.junit.annotations.TestLogging
import org.junit.Assert
import java.time.Instant

@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class DestinationRestApiIT : AlertingRestTestCase() {

    fun `test creating a chime destination`() {
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
        Assert.assertNotNull("chime object should not be null", createdDestination.chime)
    }

    fun `test updating a chime destination`() {
        val destination = createDestination()
        val chime = Chime("http://updated.com")
        var updatedDestination = updateDestination(
                destination.copy(name = "updatedName", chime = chime, type = DestinationType.CHIME))
        assertEquals("Incorrect destination name after update", updatedDestination.name, "updatedName")
        assertEquals("Incorrect destination ID after update", updatedDestination.id, destination.id)
        assertEquals("Incorrect destination type after update", updatedDestination.type, DestinationType.CHIME)
        assertEquals("Incorrect destination url after update", "http://updated.com", updatedDestination.chime?.url)
        val updatedChime = Chime("http://updated2.com")
        updatedDestination = updateDestination(
                destination.copy(id = destination.id, name = "updatedName", chime = updatedChime, type = DestinationType.CHIME))
        assertEquals("Incorrect destination url after update", "http://updated2.com", updatedDestination.chime?.url)
    }

    fun `test creating a slack destination`() {
        val slack = Slack("http://abc.com")
        val destination = Destination(
                type = DestinationType.SLACK,
                name = "test",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = slack,
                customWebhook = null,
                email = null)
        val createdDestination = createDestination(destination = destination)
        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.SLACK)
        Assert.assertNotNull("slack object should not be null", createdDestination.slack)
    }

    fun `test updating a slack destination`() {
        val destination = createDestination()
        val slack = Slack("http://updated.com")
        var updatedDestination = updateDestination(
                destination.copy(name = "updatedName", slack = slack, type = DestinationType.SLACK))
        assertEquals("Incorrect destination name after update", updatedDestination.name, "updatedName")
        assertEquals("Incorrect destination ID after update", updatedDestination.id, destination.id)
        assertEquals("Incorrect destination type after update", updatedDestination.type, DestinationType.SLACK)
        assertEquals("Incorrect destination url after update", "http://updated.com", updatedDestination.slack?.url)
        val updatedSlack = Slack("http://updated2.com")
        updatedDestination = updateDestination(
                destination.copy(name = "updatedName", slack = updatedSlack, type = DestinationType.SLACK))
        assertEquals("Incorrect destination url after update", "http://updated2.com", updatedDestination.slack?.url)
    }

    fun `test creating a custom webhook destination with url`() {
        val customWebhook = CustomWebhook("http://abc.com", null, null, 80, null, "PUT", emptyMap(), emptyMap(), null, null)
        val destination = Destination(
                type = DestinationType.CUSTOM_WEBHOOK,
                name = "test",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = null,
                customWebhook = customWebhook,
                email = null)
        val createdDestination = createDestination(destination = destination)
        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.CUSTOM_WEBHOOK)
        Assert.assertNotNull("custom webhook object should not be null", createdDestination.customWebhook)
    }

    fun `test creating a custom webhook destination with host`() {
        val customWebhook = CustomWebhook("", "http", "abc.com", 80, "a/b/c", "PATCH",
                mapOf("foo" to "1", "bar" to "2"), mapOf("h1" to "1", "h2" to "2"), null, null)
        val destination = Destination(
                type = DestinationType.CUSTOM_WEBHOOK,
                name = "test",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = null,
                customWebhook = customWebhook,
                email = null)
        val createdDestination = createDestination(destination = destination)
        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.CUSTOM_WEBHOOK)
        assertEquals("Incorrect destination host", createdDestination.customWebhook?.host, "abc.com")
        assertEquals("Incorrect destination port", createdDestination.customWebhook?.port, 80)
        assertEquals("Incorrect destination path", createdDestination.customWebhook?.path, "a/b/c")
        assertEquals("Incorrect destination scheme", createdDestination.customWebhook?.scheme, "http")
        assertEquals("Incorrect destination method", createdDestination.customWebhook?.method, "PATCH")
        Assert.assertNotNull("custom webhook object should not be null", createdDestination.customWebhook)
    }

    fun `test updating a custom webhook destination`() {
        val destination = createDestination()
        val customWebhook = CustomWebhook("http://update1.com", "http", "abc.com", 80, null, null, emptyMap(), emptyMap(), null, null)
        var updatedDestination = updateDestination(
                destination.copy(name = "updatedName", customWebhook = customWebhook,
                        type = DestinationType.CUSTOM_WEBHOOK))
        assertEquals("Incorrect destination name after update", updatedDestination.name, "updatedName")
        assertEquals("Incorrect destination ID after update", updatedDestination.id, destination.id)
        assertEquals("Incorrect destination type after update", updatedDestination.type, DestinationType.CUSTOM_WEBHOOK)
        assertEquals("Incorrect destination url after update", "http://update1.com", updatedDestination.customWebhook?.url)
        var updatedCustomWebhook = CustomWebhook("http://update2.com", "http", "abc.com", 80,
                null, "PUT", emptyMap(), emptyMap(), null, null)
        updatedDestination = updateDestination(
                destination.copy(name = "updatedName", customWebhook = updatedCustomWebhook,
                        type = DestinationType.CUSTOM_WEBHOOK))
        assertEquals("Incorrect destination url after update", "http://update2.com", updatedDestination.customWebhook?.url)
        assertEquals("Incorrect method after update", "PUT", updatedDestination.customWebhook?.method)
        updatedCustomWebhook = CustomWebhook("", "http", "abc.com", 80, null, null, emptyMap(), emptyMap(), null, null)
        updatedDestination = updateDestination(
                destination.copy(name = "updatedName", customWebhook = updatedCustomWebhook,
                        type = DestinationType.CUSTOM_WEBHOOK))
        assertEquals("Incorrect destination url after update", "abc.com", updatedDestination.customWebhook?.host)
    }

    fun `test creating an email destination`() {
        val recipient = Recipient(type = Recipient.RecipientType.EMAIL, emailGroupID = null, email = "test@email.com")
        val email = Email("", listOf(recipient))
        val destination = Destination(
                type = DestinationType.EMAIL,
                name = "test",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = null,
                customWebhook = null,
                email = email)

        val createdDestination = createDestination(destination = destination)
        Assert.assertNotNull("Email object should not be null", createdDestination.email)
        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.EMAIL)
        assertEquals("Incorrect email destination recipient type", createdDestination.email?.recipients?.get(0)?.type,
                Recipient.RecipientType.EMAIL)
        assertEquals("Incorrect email destination recipient email", createdDestination.email?.recipients?.get(0)?.email,
                "test@email.com")
    }

    fun `test updating an email destination`() {
        val destination = createDestination()
        val recipient = Recipient(type = Recipient.RecipientType.EMAIL, emailGroupID = null, email = "test@email.com")
        val email = Email("", listOf(recipient))

        var updatedDestination = updateDestination(destination.copy(type = DestinationType.EMAIL, name = "updatedName", email = email))
        Assert.assertNotNull("Email object should not be null", updatedDestination.email)
        assertEquals("Incorrect destination name after update", updatedDestination.name, "updatedName")
        assertEquals("Incorrect destination ID after update", updatedDestination.id, destination.id)
        assertEquals("Incorrect destination type after update", updatedDestination.type, DestinationType.EMAIL)
        assertEquals("Incorrect email destination recipient type after update", updatedDestination.email?.recipients?.get(0)?.type,
                Recipient.RecipientType.EMAIL)
        assertEquals("Incorrect email destination recipient email after update",
                updatedDestination.email?.recipients?.get(0)?.email, "test@email.com")

        val updatedRecipient = Recipient(type = Recipient.RecipientType.EMAIL_GROUP, emailGroupID = "testID", email = null)
        val updatedEmail = Email("testEmailAccountID", listOf(updatedRecipient))
        Assert.assertNotNull("Email object should not be null", updatedDestination.email)
        updatedDestination = updateDestination(destination.copy(type = DestinationType.EMAIL, name = "updatedName", email = updatedEmail))
        assertEquals("Incorrect email destination recipient type after update", updatedDestination.email?.recipients?.get(0)?.type,
                Recipient.RecipientType.EMAIL_GROUP)
        assertEquals("Incorrect email destination recipient email group ID after update",
                updatedDestination.email?.recipients?.get(0)?.emailGroupID, "testID")
    }

    @Throws(Exception::class)
    fun `test creating a destination`() {
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

        val createResponse = client().makeRequest("POST", DESTINATION_BASE_URI, emptyMap(), destination.toHttpEntity())

        assertEquals("Create destination failed", RestStatus.CREATED, createResponse.restStatus())
        val responseBody = createResponse.asMap()
        val createdId = responseBody["_id"] as String
        val createdVersion = responseBody["_version"] as Int
        assertNotEquals("response is missing Id", Destination.NO_ID, createdId)
        assertTrue("incorrect version", createdVersion > 0)
        assertEquals("Incorrect Location header", "$DESTINATION_BASE_URI/$createdId", createResponse.getHeader("Location"))
    }

    fun `test creating a disallowed destination fails`() {
        try {
            // Remove Slack from the allow_list
            val allowedDestinations = DestinationType.values().toList()
                .filter { destinationType -> destinationType != DestinationType.SLACK }
                .joinToString(prefix = "[", postfix = "]") { string -> "\"$string\"" }
            client().updateSettings(DestinationSettings.ALLOW_LIST.key, allowedDestinations)

            val slack = Slack("http://abc.com")
            val destination = Destination(
                type = DestinationType.SLACK,
                name = "test",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = slack,
                customWebhook = null,
                email = null)
            createDestination(destination = destination)
            fail("Expected 403 Method FORBIDDEN response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.FORBIDDEN, e.response.restStatus())
        }
    }

    fun `test delete destination`() {
        val destination = createDestination()
        val deletedDestinationResponse = client().makeRequest("DELETE", "$DESTINATION_BASE_URI/${destination.id}")
        assertEquals("Delete request not successful", RestStatus.OK, deletedDestinationResponse.restStatus())
    }

    fun `test get destination`() {
        val destination = createDestination()
        val getDestinationResponse = getDestination(destination)
        assertEquals(destination.id, getDestinationResponse["id"])
        assertEquals(destination.type.value, getDestinationResponse["type"])
        assertEquals(destination.seqNo, getDestinationResponse["seq_no"])
        assertEquals(destination.lastUpdateTime.toEpochMilli(), getDestinationResponse["last_update_time"])
        assertEquals(destination.primaryTerm, getDestinationResponse["primary_term"])
    }

    fun `test get destinations with slack destination type`() {
        val slack = Slack("url")
        val dest = Destination(
                type = DestinationType.SLACK,
                name = "testSlack",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = slack,
                customWebhook = null,
                email = null)

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"
        inputMap["destinationType"] = "slack"

        val destination = createDestination(dest)
        val destination2 = createDestination()
        val getDestinationsResponse = getDestinations(inputMap)

        assertEquals(1, getDestinationsResponse.size)
        val getDestinationResponse = getDestinationsResponse[0]

        assertEquals(destination.id, getDestinationResponse["id"])
        assertNotEquals(destination2.id, getDestinationResponse["id"])
        assertEquals(destination.type.value, getDestinationResponse["type"])
        assertEquals(destination.seqNo, getDestinationResponse["seq_no"])
        assertEquals(destination.lastUpdateTime.toEpochMilli(), getDestinationResponse["last_update_time"])
        assertEquals(destination.primaryTerm, getDestinationResponse["primary_term"])
    }

    fun `test get destinations matching a given name`() {
        val slack = Slack("url")
        val dest = Destination(
                type = DestinationType.SLACK,
                name = "testSlack",
                user = randomUser(),
                lastUpdateTime = Instant.now(),
                chime = null,
                slack = slack,
                customWebhook = null,
                email = null)

        val inputMap = HashMap<String, Any>()
        inputMap["searchString"] = "testSlack"

        val destination = createDestination(dest)
        val destination2 = createDestination()
        val getDestinationsResponse = getDestinations(inputMap)

        assertEquals(1, getDestinationsResponse.size)
        val getDestinationResponse = getDestinationsResponse[0]

        assertEquals(destination.id, getDestinationResponse["id"])
        assertNotEquals(destination2.id, getDestinationResponse["id"])
        assertEquals(destination.type.value, getDestinationResponse["type"])
        assertEquals(destination.seqNo, getDestinationResponse["seq_no"])
        assertEquals(destination.lastUpdateTime.toEpochMilli(), getDestinationResponse["last_update_time"])
        assertEquals(destination.primaryTerm, getDestinationResponse["primary_term"])
    }
}
