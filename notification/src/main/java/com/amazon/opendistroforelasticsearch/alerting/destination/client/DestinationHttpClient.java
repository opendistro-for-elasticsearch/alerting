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
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import org.apache.commons.net.util.SubnetUtils;


/**
 * This class handles the connections to the given Destination.
 */
public class DestinationHttpClient {

    private static final Logger logger = LogManager.getLogger(DestinationHttpClient.class);

    private static final int MAX_CONNECTIONS = 60;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 20;
    private static final int TIMEOUT_MILLISECONDS = (int) TimeValue.timeValueSeconds(5).millis();
    private static final int SOCKET_TIMEOUT_MILLISECONDS = (int)TimeValue.timeValueSeconds(50).millis();

    private static final List<String> blocklistRanges = new ArrayList<>(
            Arrays.asList(
                //Loopback
                "127.0.0.0/8",
                //Link-local address
                "169.254.0.0/16",
                //Reserved IP address
                "0.0.0.0/8",
                "100.64.0.0/10",
                "192.0.0.0/24",
                "192.0.2.0/24",
                "198.18.0.0/15",
                "192.88.99.0/24",
                "198.51.100.0/24",
                "203.0.113.0/24",
                "224.0.0.0/4",
                "240.0.0.0/4"));
    /**
     * all valid response status
     */
    private static final Set<Integer> VALID_RESPONSE_STATUS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(RestStatus.OK.getStatus(), RestStatus.CREATED.getStatus(), RestStatus.ACCEPTED.getStatus(),
            RestStatus.NON_AUTHORITATIVE_INFORMATION.getStatus(), RestStatus.NO_CONTENT.getStatus(),
            RestStatus.RESET_CONTENT.getStatus(), RestStatus.PARTIAL_CONTENT.getStatus(),
            RestStatus.MULTI_STATUS.getStatus())));

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
                .disableRedirectHandling()
                .setRetryHandler(new DefaultHttpRequestRetryHandler())
                .useSystemProperties()
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
        HttpRequestBase httpRequest;
        if (message instanceof CustomWebhookMessage) {
            CustomWebhookMessage customWebhookMessage = (CustomWebhookMessage) message;
            uri = buildUri(customWebhookMessage.getUrl(), customWebhookMessage.getScheme(), customWebhookMessage.getHost(),
                    customWebhookMessage.getPort(), customWebhookMessage.getPath(), customWebhookMessage.getQueryParams());
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
             uri = buildUri(message.getUrl().trim(), null, null, -1, null, null);
        }

        SubnetUtils utils;
        for (String range : blocklistRanges) {
            utils = new SubnetUtils(range);
            if (utils.getInfo().isInRange(InetAddress.getByName(uri.getHost()).getHostAddress())) {
                logger.error("Host: {} resolves to: {} which is in blocklist: {}.", uri.getHost(), InetAddress.getByName(uri.getHost()), range);
                throw new IllegalArgumentException("The destination address is invalid.");
            }
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

    private URI buildUri(String endpoint, String scheme, String host, int port, String path, Map<String, String> queryParams) throws Exception {
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
