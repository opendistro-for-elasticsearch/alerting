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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;


/**
 * This class handles the connections to the given Destination.
 */
public class DestinationHttpClient {

    private static final Logger logger = Loggers.getLogger(DestinationHttpClient.class);

    private static final int MAX_CONNECTIONS = 60;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 20;
    private static final int TIMEOUT_MILLISECONDS = (int) TimeValue.timeValueSeconds(5).millis();
    private static final int SOCKET_TIMEOUT_MILLISECONDS = (int)TimeValue.timeValueSeconds(50).millis();

    private static CloseableHttpClient HTTP_CLIENT = createHttpClient();

    private static CloseableHttpClient createHttpClient() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MILLISECONDS)
                .setConnectionRequestTimeout(TIMEOUT_MILLISECONDS)
                .setSocketTimeout(SOCKET_TIMEOUT_MILLISECONDS)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(MAX_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);

        return HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setConnectionManager(connectionManager)
                .setRetryHandler(new DefaultHttpRequestRetryHandler())
                .build();
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
        HttpPost httpPostRequest = new HttpPost();
        if (message instanceof CustomWebhookMessage) {
            CustomWebhookMessage customWebhookMessage = (CustomWebhookMessage) message;
            uri = buildUri(customWebhookMessage.getUrl(), customWebhookMessage.getScheme(), customWebhookMessage.getHost(),
                    customWebhookMessage.getPort(), customWebhookMessage.getPath(), customWebhookMessage.getQueryParams());

            // set headers
            Map<String, String> headerParams = customWebhookMessage.getHeaderParams();
            if(headerParams == null || headerParams.isEmpty()) {
                // set default header
                httpPostRequest.setHeader("Content-Type", "application/json");
            } else {
                for (Map.Entry<String, String> e : customWebhookMessage.getHeaderParams().entrySet())
                    httpPostRequest.setHeader(e.getKey(), e.getValue());
            }
        } else {
            uri = buildUri(message.getUrl().trim(), null, null, -1, null, null);
        }

        httpPostRequest.setURI(uri);
        StringEntity entity = new StringEntity(extractBody(message));
        httpPostRequest.setEntity(entity);

        return HTTP_CLIENT.execute(httpPostRequest);
    }

    private URI buildUri(String endpoint, String scheme, String host,
                         int port, String path, Map<String, String> queryParams)
            throws Exception {
        try {
            if(Strings.isNullOrEmpty(endpoint)) {
                logger.info("endpoint empty. Fall back to host:port/path");
                if (Strings.isNullOrEmpty(scheme)) {
                    scheme = "https";
                }
                URIBuilder uriBuilder = new URIBuilder();
                if(queryParams != null) {
                    for (Map.Entry<String, String> e : queryParams.entrySet())
                        uriBuilder.addParameter(e.getKey(), e.getValue());
                }
                return uriBuilder.setScheme(scheme).setHost(host).setPort(port).setPath(path).build();
            }
            return new URIBuilder(endpoint).build();
        } catch (URISyntaxException exception) {
            logger.error("Error occured while building Uri");
            throw new IllegalStateException("Error creating URI");
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

        if (statusCode != RestStatus.OK.getStatus()) {
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
