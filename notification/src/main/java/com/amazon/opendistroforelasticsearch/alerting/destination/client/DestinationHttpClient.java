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

package com.amazon.opendistroforelasticsearch.alerting.destination.client;

import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.CustomWebhookMessage;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * This class handles the connections to the given Destination.
 */
public class DestinationHttpClient {

    private static final Logger logger = LogManager.getLogger(DestinationHttpClient.class);

    private static final int MAX_CONNECTIONS = 60;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 20;
    private static final int TIMEOUT_MILLISECONDS = (int) TimeValue.timeValueSeconds(5).millis();
    private static final int SOCKET_TIMEOUT_MILLISECONDS = (int)TimeValue.timeValueSeconds(50).millis();

    /**
     * Watches for stale connections and evicts them.
     */
    private static class IdleConnectionMonitor extends TimerTask {

        private static final Logger logger = LogManager.getLogger(IdleConnectionMonitor.class);

        private final PoolingHttpClientConnectionManager cm;

        IdleConnectionMonitor(PoolingHttpClientConnectionManager cm) {
            this.cm = cm;
        }

        @Override
        public void run() {
            logger.debug("Trying to close expired and idle connections.");
            cm.closeExpiredConnections();
            cm.closeIdleConnections(60, TimeUnit.SECONDS);
        }
    }

    /**
     * all valid response status
     */
    private static final Set<Integer> VALID_RESPONSE_STATUS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(RestStatus.OK.getStatus(), RestStatus.CREATED.getStatus(), RestStatus.ACCEPTED.getStatus(),
            RestStatus.NON_AUTHORITATIVE_INFORMATION.getStatus(), RestStatus.NO_CONTENT.getStatus(),
            RestStatus.RESET_CONTENT.getStatus(), RestStatus.PARTIAL_CONTENT.getStatus(),
            RestStatus.MULTI_STATUS.getStatus())));

    private static CloseableHttpClient HTTP_CLIENT = createHttpClient();
    private static IdleConnectionMonitor monitor;

    private static CloseableHttpClient createHttpClient() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MILLISECONDS)
                .setConnectionRequestTimeout(TIMEOUT_MILLISECONDS)
                .setSocketTimeout(SOCKET_TIMEOUT_MILLISECONDS)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(MAX_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);

        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setConnectionManager(connectionManager)
                .useSystemProperties()
                .build();

        // Start up the eviction task
        Timer timer = new Timer(true);
        monitor = new IdleConnectionMonitor(connectionManager);
        timer.schedule(monitor, 60000L, 60000L);

        return client;
    }

    public String execute(BaseMessage message) throws Exception {
        CloseableHttpResponse response = null;
        try {
            response = getHttpResponse(message);
            validateResponseStatus(response);
            return getResponseString(response);
        } finally {
            if (response != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
    }

    private CloseableHttpResponse getHttpResponse(BaseMessage message) throws Exception {
        URI uri = null;
        HttpRequestBase httpRequest;
        if (message instanceof CustomWebhookMessage) {
            CustomWebhookMessage customWebhookMessage = (CustomWebhookMessage) message;
            uri = customWebhookMessage.getUri();
            httpRequest = constructHttpRequest(((CustomWebhookMessage) message).getMethod());
            // set headers
            Map<String, String> headerParams = customWebhookMessage.getHeaderParams();
            if(headerParams == null || headerParams.isEmpty()) {
                // set default header
                httpRequest.setHeader("Content-Type", "application/json");
            } else {
                for (Map.Entry<String, String> e : customWebhookMessage.getHeaderParams().entrySet())
                    httpRequest.setHeader(e.getKey(), e.getValue());
            }
        } else {
             httpRequest = new HttpPost();
             uri = message.getUri();
        }

        httpRequest.setURI(uri);
        if (httpRequest instanceof HttpEntityEnclosingRequestBase){
            StringEntity entity = new StringEntity(extractBody(message), StandardCharsets.UTF_8);
            ((HttpEntityEnclosingRequestBase) httpRequest).setEntity(entity);
        }

        return HTTP_CLIENT.execute(httpRequest);
    }

    private HttpRequestBase constructHttpRequest(String method) {
        switch (method){
            case "POST":
                return new HttpPost();
            case "PUT":
                return new HttpPut();
            case "PATCH":
                return new HttpPatch();
            default:
                throw new IllegalArgumentException("Invalid method supplied");
        }
    }

    public String getResponseString(CloseableHttpResponse response) throws IOException {
            HttpEntity entity = response.getEntity();
            if (entity == null)
                return "{}";

            String responseString = EntityUtils.toString(entity);
            logger.debug("Http response: " + responseString);

            return responseString;
    }

    private void validateResponseStatus(HttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();

        if (!(VALID_RESPONSE_STATUS.contains(statusCode))) {
            throw new IOException("Failed: " + response);
        }
    }

    private String extractBody(BaseMessage message) {
        return message.getMessageContent();
    }

    /*
     * This method is useful for Mocking the client
     */
     public void setHttpClient(CloseableHttpClient httpClient) {
        HTTP_CLIENT = httpClient;
    }
}
