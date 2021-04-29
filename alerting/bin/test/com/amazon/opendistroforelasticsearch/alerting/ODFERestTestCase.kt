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

import com.amazon.opendistroforelasticsearch.commons.ConfigConstants.OPENDISTRO_SECURITY_SSL_HTTP_ENABLED
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants.OPENDISTRO_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants.OPENDISTRO_SECURITY_SSL_HTTP_KEYSTORE_KEYPASSWORD
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants.OPENDISTRO_SECURITY_SSL_HTTP_KEYSTORE_PASSWORD
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants.OPENDISTRO_SECURITY_SSL_HTTP_PEMCERT_FILEPATH
import com.amazon.opendistroforelasticsearch.commons.rest.SecureRestClientBuilder
import org.apache.http.HttpHost
import org.elasticsearch.client.Request
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.WarningsHandler
import org.elasticsearch.common.io.PathUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.DeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.test.rest.ESRestTestCase
import org.junit.After
import java.io.IOException

/**
 * Must support below 3 scenario runs:
 * 1. Without Security plugin
 * 2. With Security plugin and https
 * 3. With Security plugin and http
 *    Its possible to have security enabled with http transport.
 * client() - > admin user
 * adminClient() -> adminDN/super-admin user
 */

abstract class ODFERestTestCase : ESRestTestCase() {

    fun isHttps(): Boolean {
        return System.getProperty("https", "false")!!.toBoolean()
    }

    fun securityEnabled(): Boolean {
        return System.getProperty("security", "false")!!.toBoolean()
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
    open fun wipeAllODFEIndices() {
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
                    var request = Request("DELETE", "/$indexName")
                    // TODO: remove PERMISSIVE option after moving system index access to REST API call
                    val options = RequestOptions.DEFAULT.toBuilder()
                    options.setWarningsHandler(WarningsHandler.PERMISSIVE)
                    request.options = options.build()
                    adminClient().performRequest(request)
                }
            }
        }
    }

    /**
     * Returns the REST client settings used for super-admin actions like cleaning up after the test has completed.
     */
    override fun restAdminSettings(): Settings {
        return Settings
                .builder()
                .put("http.port", 9200)
                .put(OPENDISTRO_SECURITY_SSL_HTTP_ENABLED, isHttps())
                .put(OPENDISTRO_SECURITY_SSL_HTTP_PEMCERT_FILEPATH, "sample.pem")
                .put(OPENDISTRO_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH, "test-kirk.jks")
                .put(OPENDISTRO_SECURITY_SSL_HTTP_KEYSTORE_PASSWORD, "changeit")
                .put(OPENDISTRO_SECURITY_SSL_HTTP_KEYSTORE_KEYPASSWORD, "changeit")
                .build()
    }

    @Throws(IOException::class)
    override fun buildClient(settings: Settings, hosts: Array<HttpHost>): RestClient {
        if (securityEnabled()) {
            val keystore = settings.get(OPENDISTRO_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH)
            return when (keystore != null) {
                true -> {
                    // create adminDN (super-admin) client
                    val uri = javaClass.classLoader.getResource("sample.pem").toURI()
                    val configPath = PathUtils.get(uri).parent.toAbsolutePath()
                    SecureRestClientBuilder(settings, configPath).setSocketTimeout(60000).build()
                }
                false -> {
                    // create client with passed user
                    val userName = System.getProperty("user")
                    val password = System.getProperty("password")
                    SecureRestClientBuilder(hosts, isHttps(), userName, password).setSocketTimeout(60000).build()
                }
            }
        } else {
            val builder = RestClient.builder(*hosts)
            configureClient(builder, settings)
            builder.setStrictDeprecationMode(true)
            return builder.build()
        }
    }
}
