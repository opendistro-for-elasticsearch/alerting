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

package com.amazon.opendistroforelasticsearch.alerting.client

import com.amazon.opendistroforelasticsearch.alerting.core.model.HttpInput
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.common.unit.TimeValue
import java.security.AccessController
import java.security.PrivilegedAction

/**
 * This class takes [HttpInput] and performs GET requests to given URIs.
 */
class HttpInputClient {

    // TODO: If possible, these settings should be implemented as changeable via the "_cluster/settings" API.
    private val CONNECTION_TIMEOUT_MILLISECONDS = TimeValue.timeValueSeconds(10).millis().toInt()
    private val REQUEST_TIMEOUT_MILLISECONDS = TimeValue.timeValueSeconds(10).millis().toInt()
    private val SOCKET_TIMEOUT_MILLISECONDS = TimeValue.timeValueSeconds(10).millis().toInt()

    val client = createHttpClient()

    /**
     * Create [CloseableHttpAsyncClient] as a [PrivilegedAction] in order to avoid [java.net.NetPermission] error.
     */
    private fun createHttpClient(): CloseableHttpAsyncClient {
        val config = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT_MILLISECONDS)
                .setConnectionRequestTimeout(REQUEST_TIMEOUT_MILLISECONDS)
                .setSocketTimeout(SOCKET_TIMEOUT_MILLISECONDS)
                .build()

        return AccessController.doPrivileged(PrivilegedAction<CloseableHttpAsyncClient>({
            HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(config)
                    .useSystemProperties()
                    .build()
        } as () -> CloseableHttpAsyncClient))
    }
}
