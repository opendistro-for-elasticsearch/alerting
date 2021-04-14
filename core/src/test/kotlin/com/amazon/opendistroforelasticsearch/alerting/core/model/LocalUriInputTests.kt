package com.amazon.opendistroforelasticsearch.alerting.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalUriInputTests {
    private var scheme = "http"
    private var host = "localhost"
    private var port = 9200
    private var path = "/_cluster/health"
    private var queryParams = hashMapOf<String, String>()
    private var url = ""
    private var connectionTimeout = 5
    private var socketTimeout = 5

    @Test
    fun `test valid LocalUriInput creation using HTTP URI component fields`() {
        // GIVEN + WHEN
        val localUriInput = LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout)

        // THEN
        assertEquals(scheme, localUriInput.scheme)
        assertEquals(host, localUriInput.host)
        assertEquals(port, localUriInput.port)
        assertEquals(path, localUriInput.path)
        assertEquals(queryParams, localUriInput.query_params)
        assertEquals(url, localUriInput.url)
        assertEquals(connectionTimeout, localUriInput.connection_timeout)
        assertEquals(socketTimeout, localUriInput.socket_timeout)
    }

    @Test
    fun `test valid LocalUriInput creation using HTTP url field`() {
        // GIVEN
        scheme = ""
        host = ""
        port = -1
        path = ""
        url = "http://localhost:9200/_cluster/health"

        // WHEN
        val localUriInput = LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout)

        // THEN
        assertEquals(url, localUriInput.url)
    }

    @Test
    fun `test valid LocalUriInput creation using HTTPS URI component fields`() {
        // GIVEN
        scheme = "https"

        // WHEN
        val localUriInput = LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout)

        // THEN
        assertEquals(scheme, localUriInput.scheme)
        assertEquals(host, localUriInput.host)
        assertEquals(port, localUriInput.port)
        assertEquals(path, localUriInput.path)
        assertEquals(queryParams, localUriInput.query_params)
        assertEquals(url, localUriInput.url)
        assertEquals(connectionTimeout, localUriInput.connection_timeout)
        assertEquals(socketTimeout, localUriInput.socket_timeout)
    }

    @Test
    fun `test valid LocalUriInput creation using HTTPS url field`() {
        // GIVEN
        scheme = ""
        host = ""
        port = -1
        path = ""
        url = "https://localhost:9200/_cluster/health"

        // WHEN
        val localUriInput = LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout)

        // THEN
        assertEquals(url, localUriInput.url)
    }

    @Test
    fun `test invalid scheme`() {
        // GIVEN
        scheme = "invalidScheme"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Invalid url: $scheme://$host:$port$path") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid host`() {
        // GIVEN
        host = "loco//host"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Invalid url: $scheme://$host:$port$path") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid host is not localhost`() {
        // GIVEN
        host = "127.0.0.1"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>(
            "Only host '${LocalUriInput.SUPPORTED_HOST}' is supported. Host: $host") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid path`() {
        // GIVEN
        path = "///"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Invalid url: $scheme://$host:$port$path") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid port`() {
        // GIVEN
        port = LocalUriInput.SUPPORTED_PORT + 1

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>(
            "Only port '${LocalUriInput.SUPPORTED_PORT}' is supported. Port: $port") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid connection timeout that's too low`() {
        // GIVEN
        connectionTimeout = LocalUriInput.MIN_CONNECTION_TIMEOUT - 1

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>(
            "Connection timeout: $connectionTimeout is not in the range of ${LocalUriInput.MIN_CONNECTION_TIMEOUT} - ${LocalUriInput.MIN_CONNECTION_TIMEOUT}") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid connection timeout that's too high`() {
        // GIVEN
        connectionTimeout = LocalUriInput.MAX_CONNECTION_TIMEOUT + 1

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>(
            "Connection timeout: $connectionTimeout is not in the range of ${LocalUriInput.MIN_CONNECTION_TIMEOUT} - ${LocalUriInput.MIN_CONNECTION_TIMEOUT}") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid socket timeout that's too low`() {
        // GIVEN
        socketTimeout = LocalUriInput.MIN_SOCKET_TIMEOUT - 1

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>(
            "Socket timeout: $socketTimeout is not in the range of ${LocalUriInput.MIN_SOCKET_TIMEOUT} - ${LocalUriInput.MAX_SOCKET_TIMEOUT}") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid socket timeout that's too high`() {
        // GIVEN
        socketTimeout = LocalUriInput.MAX_SOCKET_TIMEOUT + 1

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>(
            "Socket timeout: $socketTimeout is not in the range of ${LocalUriInput.MIN_SOCKET_TIMEOUT} - ${LocalUriInput.MAX_SOCKET_TIMEOUT}") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid url`() {
        // GIVEN
        url = "///"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Invalid url: $scheme://$host:$port$path") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test setting other fields in addition to url field`() {
        // GIVEN
        url = "http://localhost:9200/_cluster/health"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Either the url field, or scheme + host + port + path + params can be set.") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test LocalUriInput creation when all inputs are empty`() {
        // GIVEN
        scheme = ""
        host = ""
        port = -1
        path = ""
        url = ""

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Either the url field, or scheme + host + port + path + params can be set.") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid host in url field`() {
        // GIVEN
        scheme = ""
        host = ""
        port = -1
        path = ""
        url = "http://127.0.0.1:9200/_cluster/health"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Only host '${LocalUriInput.SUPPORTED_HOST}' is supported. Host: $host") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }

    @Test
    fun `test invalid port in url field`() {
        // GIVEN
        scheme = ""
        host = ""
        port = -1
        path = ""
        url = "http://localhost:${LocalUriInput.SUPPORTED_PORT + 1}/_cluster/health"

        // WHEN + THEN
        assertFailsWith<IllegalArgumentException>("Only port '${LocalUriInput.SUPPORTED_PORT}' is supported. Port: $port") {
            LocalUriInput(scheme, host, port, path, queryParams, url, connectionTimeout, socketTimeout) }
    }
}