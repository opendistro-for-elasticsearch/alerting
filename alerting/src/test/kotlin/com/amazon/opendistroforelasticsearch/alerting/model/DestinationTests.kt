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

import com.amazon.opendistroforelasticsearch.alerting.model.destination.Chime
import com.amazon.opendistroforelasticsearch.alerting.model.destination.CustomWebhook
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.Email
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Slack
import com.amazon.opendistroforelasticsearch.alerting.parser
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.Recipient
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.test.ESTestCase
import java.time.Instant

class DestinationTests : ESTestCase() {

    fun `test chime destination`() {
        val chime = Chime("http://abc.com")
        assertEquals("Url is manipulated", chime.url, "http://abc.com")
    }

    fun `test chime destination with out url`() {
        try {
            Chime("")
            fail("Creating a chime destination with empty url did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test slack destination`() {
        val slack = Slack("http://abc.com")
        assertEquals("Url is manipulated", slack.url, "http://abc.com")
    }

    fun `test slack destination with out url`() {
        try {
            Slack("")
            fail("Creating a slack destination with empty url did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test email destination without recipients`() {
        try {
            Email("", emptyList())
            fail("Creating an email destination with empty recipients did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test email recipient with valid email`() {
        Recipient(
            Recipient.RecipientType.EMAIL,
            null,
            "test@email.com"
        )
    }

    fun `test email recipient with invalid email fails`() {
        try {
            Recipient(
                Recipient.RecipientType.EMAIL,
                null,
                "invalid@email"
            )
            fail("Creating an email recipient with an invalid email did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test custom webhook destination with url and no host`() {
        val customWebhook = CustomWebhook("http://abc.com", null, null, -1, null, null, emptyMap(), emptyMap(), null, null)
        assertEquals("Url is manipulated", customWebhook.url, "http://abc.com")
    }

    fun `test custom webhook destination with host and no url`() {
        try {
            val customWebhook = CustomWebhook(null, null, "abc.com", 80, null, null, emptyMap(), emptyMap(), null, null)
            assertEquals("host is manipulated", customWebhook.host, "abc.com")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test custom webhook destination with url and host`() {
        // In this case, url will be given priority
        val customWebhook = CustomWebhook("http://abc.com", null, null, -1, null, null, emptyMap(), emptyMap(), null, null)
        assertEquals("Url is manipulated", customWebhook.url, "http://abc.com")
    }

    fun `test custom webhook destination with no url and no host`() {
        try {
            CustomWebhook("", null, null, 80, null, null, emptyMap(), emptyMap(), null, null)
            fail("Creating a custom webhook destination with empty url did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test chime destination create using stream`() {
        val chimeDest = Destination("1234", 0L, 1, 1, 1, DestinationType.CHIME, "TestChimeDest",
        randomUser(), Instant.now(), Chime("test.com"), null, null, null)

        val out = BytesStreamOutput()
        chimeDest.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newDest = Destination.readFrom(sin)

        assertNotNull(newDest)
        assertEquals("1234", newDest.id)
        assertEquals(0, newDest.version)
        assertEquals(1, newDest.schemaVersion)
        assertEquals(DestinationType.CHIME, newDest.type)
        assertEquals("TestChimeDest", newDest.name)
        assertNotNull(newDest.lastUpdateTime)
        assertNotNull(newDest.chime)
        assertNull(newDest.slack)
        assertNull(newDest.customWebhook)
        assertNull(newDest.email)
    }

    fun `test slack destination create using stream`() {
        val slackDest = Destination("2345", 1L, 2, 1, 1, DestinationType.SLACK, "TestSlackDest",
                randomUser(), Instant.now(), null, Slack("mytest.com"), null, null)

        val out = BytesStreamOutput()
        slackDest.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newDest = Destination.readFrom(sin)

        assertNotNull(newDest)
        assertEquals("2345", newDest.id)
        assertEquals(1, newDest.version)
        assertEquals(2, newDest.schemaVersion)
        assertEquals(DestinationType.SLACK, newDest.type)
        assertEquals("TestSlackDest", newDest.name)
        assertNotNull(newDest.lastUpdateTime)
        assertNull(newDest.chime)
        assertNotNull(newDest.slack)
        assertNull(newDest.customWebhook)
        assertNull(newDest.email)
    }

    fun `test customwebhook destination create using stream`() {
        val customWebhookDest = Destination(
                "2345",
                1L,
                2,
                1,
                1,
                DestinationType.SLACK,
                "TestSlackDest",
                randomUser(),
                Instant.now(),
                null,
                null,
                CustomWebhook(
                    "test.com",
                    "schema",
                    "localhost",
                    162,
                    "/tmp/",
                    "POST",
                    mutableMapOf(),
                    mutableMapOf(),
                    "admin",
                    "admin"
                ),
                null
        )
        val out = BytesStreamOutput()
        customWebhookDest.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newDest = Destination.readFrom(sin)

        assertNotNull(newDest)
        assertEquals("2345", newDest.id)
        assertEquals(1, newDest.version)
        assertEquals(2, newDest.schemaVersion)
        assertEquals(DestinationType.SLACK, newDest.type)
        assertEquals("TestSlackDest", newDest.name)
        assertNotNull(newDest.lastUpdateTime)
        assertNull(newDest.chime)
        assertNull(newDest.slack)
        assertNotNull(newDest.customWebhook)
        assertNull(newDest.email)
    }

    fun `test customwebhook destination create using stream with optionals`() {
        val customWebhookDest = Destination(
                "2345",
                1L,
                2,
                1,
                1,
                DestinationType.SLACK,
                "TestSlackDest",
                randomUser(),
                Instant.now(),
                null,
                null,
                CustomWebhook(
                        "test.com",
                        null,
                        "localhost",
                        162,
                        null,
                        "POST",
                        mutableMapOf(),
                        mutableMapOf(),
                        null,
                        null
                ),
                null
        )
        val out = BytesStreamOutput()
        customWebhookDest.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newDest = Destination.readFrom(sin)

        assertNotNull(newDest)
        assertEquals("2345", newDest.id)
        assertEquals(1, newDest.version)
        assertEquals(2, newDest.schemaVersion)
        assertEquals(DestinationType.SLACK, newDest.type)
        assertEquals("TestSlackDest", newDest.name)
        assertNotNull(newDest.lastUpdateTime)
        assertNull(newDest.chime)
        assertNull(newDest.slack)
        assertNotNull(newDest.customWebhook)
        assertNull(newDest.email)
    }

    fun `test email destination create using stream`() {
        val recipients = listOf(
                Recipient(
                    Recipient.RecipientType.EMAIL,
                    null,
                    "test@email.com"
                )
        )
        val mailDest = Destination(
                "2345",
                1L,
                2,
                1,
                1,
                DestinationType.EMAIL,
                "TestEmailDest",
                randomUser(),
                Instant.now(),
                null,
                null,
                null,
                Email("3456", recipients)
        )

        val out = BytesStreamOutput()
        mailDest.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newDest = Destination.readFrom(sin)

        assertNotNull(newDest)
        assertEquals("2345", newDest.id)
        assertEquals(1, newDest.version)
        assertEquals(2, newDest.schemaVersion)
        assertEquals(DestinationType.EMAIL, newDest.type)
        assertEquals("TestEmailDest", newDest.name)
        assertNotNull(newDest.lastUpdateTime)
        assertNull(newDest.chime)
        assertNull(newDest.slack)
        assertNull(newDest.customWebhook)
        assertNotNull(newDest.email)

        assertEquals("3456", newDest.email!!.emailAccountID)
        assertEquals(recipients, newDest.email!!.recipients)
    }

    fun `test chime destination without user`() {
        val userString = "{\"type\":\"chime\",\"name\":\"TestChimeDest\",\"schema_version\":1," +
                "\"last_update_time\":1600063313658,\"chime\":{\"url\":\"test.com\"}}"
        val parsedDest = Destination.parse(parser(userString))
        assertNull(parsedDest.user)
    }

    fun `test chime destination with user`() {
        val userString = "{\"type\":\"chime\",\"name\":\"TestChimeDest\",\"user\":{\"name\":\"joe\",\"backend_roles\"" +
                ":[\"ops\",\"backup\"],\"roles\":[\"ops_role, backup_role\"],\"custom_attribute_names\":[\"test_attr=test\"]}," +
                "\"schema_version\":1,\"last_update_time\":1600063313658,\"chime\":{\"url\":\"test.com\"}}"
        val parsedDest = Destination.parse(parser(userString))
        assertNotNull(parsedDest.user)
    }

    fun `test chime destination with user as null`() {
        val userString = "{\"type\":\"chime\",\"name\":\"TestChimeDest\",\"user\":null,\"schema_version\":1," +
            "\"last_update_time\":1600063313658,\"chime\":{\"url\":\"test.com\"}}"
        val parsedDest = Destination.parse(parser(userString))
        assertNull(parsedDest.user)
    }
}
