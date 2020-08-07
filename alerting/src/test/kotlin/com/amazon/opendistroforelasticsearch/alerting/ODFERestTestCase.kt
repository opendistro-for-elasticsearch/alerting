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

package com.amazon.opendistroforelasticsearch.alerting

import org.apache.http.Header
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.message.BasicHeader
import org.apache.http.ssl.SSLContextBuilder
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.util.concurrent.ThreadContext
import org.elasticsearch.common.xcontent.DeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.test.rest.ESRestTestCase
import org.junit.After
import java.io.IOException
import java.security.cert.X509Certificate

abstract class ODFERestTestCase : ESRestTestCase() {

    private fun isHttps(): Boolean {
        return System.getProperty("https", "false")!!.toBoolean()
    }

    override fun getProtocol(): String {
        return if (isHttps()) {
            "https"
        } else {
            "http"
        }
    }

    override fun preserveIndicesUponCompletion(): Boolean {
        return true
    }

    @Throws(IOException::class)
    @After
    public open fun wipeAllODFEIndices() {
        val response = client().performRequest(Request("GET", "/_cat/indices?format=json&expand_wildcards=all"))

        val xContentType = XContentType.fromMediaTypeOrFormat(response.entity.contentType.value)
        xContentType.xContent().createParser(
                NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                response.entity.content).use { parser ->
            for (index in parser.list()) {
                val jsonObject: Map<*, *> = index as java.util.HashMap<*, *>
                val indexName: String = jsonObject["index"] as String
                // .opendistro_security isn't allowed to delete from cluster
                if (".opendistro_security" != indexName) {
                    client().performRequest(Request("DELETE", "/$indexName"))
                }
            } }
    }
    @Throws(IOException::class)
    override fun buildClient(settings: Settings, hosts: Array<HttpHost>): RestClient {
        val builder = RestClient.builder(*hosts)
        if (isHttps()) {
            configureHttpsClient(builder, settings)
        } else {
            configureClient(builder, settings)
        }
        builder.setStrictDeprecationMode(true)
        return builder.build()
    }

    @Throws(IOException::class)
    protected open fun configureHttpsClient(builder: RestClientBuilder, settings: Settings) {
        val headers = ThreadContext.buildDefaultHeaders(settings)
        val defaultHeaders = arrayOfNulls<Header>(headers.size)
        var i = 0
        for ((key, value) in headers) {
            defaultHeaders[i++] = BasicHeader(key, value)
        }
        builder.setDefaultHeaders(defaultHeaders)
        builder.setHttpClientConfigCallback { httpClientBuilder: HttpAsyncClientBuilder ->
            val userName = System.getProperty("user")
            val password = System.getProperty("password")
            val credentialsProvider: CredentialsProvider = BasicCredentialsProvider()
            credentialsProvider.setCredentials(AuthScope.ANY, UsernamePasswordCredentials(userName, password))
            try {
                return@setHttpClientConfigCallback httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                        // disable the certificate since our testing cluster just uses the default security configuration
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .setSSLContext(SSLContextBuilder.create()
                                .loadTrustMaterial(null) { _: Array<X509Certificate?>?, _: String? -> true }
                                .build())
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        val socketTimeoutString = settings[CLIENT_SOCKET_TIMEOUT]
        val socketTimeout = TimeValue.parseTimeValue(socketTimeoutString ?: "60s", CLIENT_SOCKET_TIMEOUT)
        builder.setRequestConfigCallback { conf: RequestConfig.Builder -> conf.setSocketTimeout(Math.toIntExact(socketTimeout.millis)) }
        if (settings.hasValue(CLIENT_PATH_PREFIX)) {
            builder.setPathPrefix(settings[CLIENT_PATH_PREFIX])
        }
    }
}