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

import org.junit.Assert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class HttpInputTest {
    // Test invalid url with different format in one function
    @Test
    fun `test invalid urls`() {
        try {
            // Invalid scheme
            HttpInput("notAValidScheme", "localhost", 9200, "_cluster/health", mapOf(), "", 5000, 5000)
            fail("Invalid scheme when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Invalid url: notAValidScheme://localhost:9200/_cluster/health", e.message)
        }
        try {
            // Invalid host
            HttpInput("http", "loco//host", 9200, "_cluster/health", mapOf(), "", 5000, 5000)
            fail("Invalid host when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Invalid url: http://loco//host:9200/_cluster/health", e.message)
        }
        try {
            // Invalid path
            HttpInput("http", "localhost", 9200, "///", mapOf(), "", 5000, 5000)
            fail("Invalid path when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Invalid url: http://localhost:9200///", e.message)
        }
        try {
            // Invalid url
            HttpInput("http", "localhost", 9200, "_cluster/health", mapOf(), "¯¯\\_( ͡° ͜ʖ ͡°)_//¯ ", 5000, 5000)
            fail("Invalid url when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Invalid url: ¯¯\\_( ͡° ͜ʖ ͡°)_//¯ ", e.message)
        }
        try {
            // Invalid connection timeout
            HttpInput("http", "localhost", 9200, "_cluster/health", mapOf(), "", -5000, 5000)
            fail("Invalid connection timeout when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Connection timeout: -5000 is not greater than 0.", e.message)
        }
        try {
            // Invalid socket timeout
            HttpInput("http", "localhost", 9200, "_cluster/health", mapOf(), "", 5000, -5000)
            fail("Invalid socket timeout when creating HttpInput should fail.")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Socket timeout: -5000 is not greater than 0.", e.message)
        }
    }

    // Test valid url with complete url
    @Test
    fun `test valid HttpInput using url`() {
        val validHttpInput = HttpInput("", "", -1, "", mapOf(), "http://localhost:9200/_cluster/health/", 5000, 5000)
        assertEquals(validHttpInput.url, "http://localhost:9200/_cluster/health/")
        assertEquals(validHttpInput.connection_timeout, 5000)
        assertEquals(validHttpInput.socket_timeout, 5000)
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
                connection_timeout = 5000,
                socket_timeout = 2500)
        assertEquals(validHttpInput.scheme, "http")
        assertEquals(validHttpInput.host, "localhost")
        assertEquals(validHttpInput.port, 9200)
        assertEquals(validHttpInput.path, "_cluster/health")
        assertEquals(validHttpInput.params, mapOf("value" to "x", "secondVal" to "second"))
        assertEquals(validHttpInput.connection_timeout, 5000)
        assertEquals(validHttpInput.socket_timeout, 2500)
    }
}
