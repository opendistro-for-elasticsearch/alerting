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

package com.amazon.opendistroforelasticsearch.alerting.core.model

import java.net.URISyntaxException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class HttpInputTest {
    // Test invalid url with different format in one function
    @Test
    fun `test invalid urls`() {
        try {
            // Invalid scheme
            HttpInput("notAValidScheme", "localhost", 9200, "_cluster/health", mapOf(), "", 5, 5)
            fail("Invalid scheme when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid url: notAValidScheme://localhost:9200/_cluster/health", e.message)
        }
        try {
            // Invalid host
            HttpInput("http", "loco//host", 9200, "_cluster/health", mapOf(), "", 5, 5)
            fail("Invalid host when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid url: http://loco//host:9200/_cluster/health", e.message)
        }
        try {
            // Invalid path
            HttpInput("http", "localhost", 9200, "///", mapOf(), "", 5, 5)
            fail("Invalid path when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid url: http://localhost:9200///", e.message)
        }
        try {
            // Invalid url
            HttpInput("", "", -1, "", mapOf(), "¯¯\\_( ͡° ͜ʖ ͡°)_//¯ ", 5, 5)
            fail("Invalid url when creating HttpInput should fail.")
        } catch (e: URISyntaxException) {
            assertTrue(e.message.toString().contains("Illegal character in path at index"), "Error message is : ${e.message}")
        }
        try {
            // Invalid connection timeout
            HttpInput("http", "localhost", 9200, "_cluster/health", mapOf(), "", 70, 5)
            fail("Invalid connection timeout when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            assertEquals("Connection timeout: 70 is not in the range of 1 - 5", e.message)
        }
        try {
            // Invalid socket timeout
            HttpInput("http", "localhost", 9200, "_cluster/health", mapOf(), "", 5, -5)
            fail("Invalid socket timeout when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            assertEquals("Socket timeout: -5 is not in the range of 1 - 60", e.message)
        }
        try {
            // Setting other fields along with url field is not allowed
            HttpInput("http", "localhost", 9200, "_cluster/health", mapOf(), "http://localhost:9200/_cluster/health", 5, 5)
            fail("Setting url and other fields at the same time should fail.")
        } catch (e: IllegalArgumentException) {
            assertEquals("Either one of url or scheme + host + port + path + params can be set.", e.message)
        }
    }

    // Test valid url with complete url
    @Test
    fun `test valid HttpInput using url`() {
        val validHttpInput = HttpInput("", "", -1, "", mapOf(), "http://localhost:9200/_cluster/health/", 5, 5)
        assertEquals(validHttpInput.url, "http://localhost:9200/_cluster/health/")
        assertEquals(validHttpInput.connection_timeout, 5)
        assertEquals(validHttpInput.socket_timeout, 5)
    }

    @Test
    fun `test valid HttpInput created field by field`() {
        val validHttpInput = HttpInput(
                scheme = "http",
                host = "localhost",
                port = 9200,
                path = "_cluster/health",
                params = mapOf("value" to "x", "secondVal" to "second"),
                url = "",
                connection_timeout = 5,
                socket_timeout = 10)
        assertEquals(validHttpInput.scheme, "http")
        assertEquals(validHttpInput.host, "localhost")
        assertEquals(validHttpInput.port, 9200)
        assertEquals(validHttpInput.path, "_cluster/health")
        assertEquals(validHttpInput.params, mapOf("value" to "x", "secondVal" to "second"))
        assertEquals(validHttpInput.url, "")
        assertEquals(validHttpInput.connection_timeout, 5)
        assertEquals(validHttpInput.socket_timeout, 10)
    }
}
