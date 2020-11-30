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

package com.amazon.opendistroforelasticsearch.alerting.destination.message;

import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.elasticsearch.common.Strings;

import java.util.Map;

/**
 * This class holds the content of an CustomWebhook message
 */
public class CustomWebhookMessage extends BaseMessage {

    private String message;
    private String url;
    private String scheme;
    private String host;
    private String method;
    private int port;
    private String path;
    private Map<String, String> queryParams;
    private Map<String, String> headerParams;
    private final String userName;
    private final String password;

    private CustomWebhookMessage(final DestinationType destinationType,
                                 final String destinationName,
                                 final String url,
                                 final String scheme,
                                 final String host,
                                 final Integer port,
                                 final String path,
                                 final String method,
                                 final Map<String, String> queryParams,
                                 final Map<String, String> headerParams,
                                 final String userName,
                                 final String password,
                                 final String message) {

        super(destinationType, destinationName, message);

        if (DestinationType.CUSTOMWEBHOOK != destinationType) {
            throw new IllegalArgumentException("Channel Type does not match CustomWebhook");
        }

        if (!Strings.isNullOrEmpty(url)) {
            setUrl(url.trim());
        }

        if (Strings.isNullOrEmpty(message)) {
            throw new IllegalArgumentException("Message content is missing");
        }

        this.scheme = Strings.isNullOrEmpty(scheme) ? "https" : scheme;
        this.port = port==null ? -1 : port;

        if (!Strings.isNullOrEmpty(path)) {
            if (!path.startsWith("/")) {
                this.path = "/" + path;
            }
        }

        if(Strings.isNullOrEmpty(url) && Strings.isNullOrEmpty(host)) {
            throw new IllegalArgumentException("Either fully qualified URL or host name should be provided");
        }

        if (Strings.isNullOrEmpty(method)){
            // Default to POST for backwards compatibility
            this.method = "POST";
        } else if (!HttpPost.METHOD_NAME.equals(method) && !HttpPut.METHOD_NAME.equals(method)
                && !HttpPatch.METHOD_NAME.equals(method)) {
            throw new IllegalArgumentException("Invalid method supplied. Only POST, PUT and PATCH are allowed");
        } else {
            this.method = method;
        }


        this.message = message;
        this.url = url;
        this.host = host;
        this.queryParams = queryParams;
        this.headerParams = headerParams;
        this.userName = userName;
        this.password = password;
    }

    @Override
    public String toString() {
        return "DestinationType: " + destinationType + ", DestinationName:" +  destinationName +
                ", Url: " + url + ", scheme: " + scheme + ", Host: " + host + ", Port: " +
                port + ", Path: " + path + ", Method: " + method + ", Message: " + message;
    }

    public static class Builder {
        private String message;
        private DestinationType destinationType;
        private String destinationName;
        private String url;
        private String scheme;
        private String host;
        private Integer port;
        private String path;
        private String method;
        private Map<String, String> queryParams;
        private Map<String, String> headerParams;
        private String userName;
        private String password;

        public Builder(String destinationName) {
            this.destinationName = destinationName;
            this.destinationType = DestinationType.CUSTOMWEBHOOK;
        }

        public CustomWebhookMessage.Builder withScheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public CustomWebhookMessage.Builder withHost(String host) {
            this.host = host;
            return this;
        }

        public CustomWebhookMessage.Builder withPort(Integer port) {
            this.port = port;
            return this;
        }

        public CustomWebhookMessage.Builder withPath(String path) {
            this.path = path;
            return this;
        }

        public CustomWebhookMessage.Builder withMethod(String method) {
            this.method = method;
            return this;
        }

        public CustomWebhookMessage.Builder withQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public CustomWebhookMessage.Builder withHeaderParams(Map<String, String> headerParams) {
            this.headerParams = headerParams;
            return this;
        }

        public CustomWebhookMessage.Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public CustomWebhookMessage.Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public CustomWebhookMessage.Builder withUserName(String userName) {
            this.userName = userName;
            return this;
        }

        public CustomWebhookMessage.Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public CustomWebhookMessage build() {
            CustomWebhookMessage customWebhookMessage = new CustomWebhookMessage(
                    this.destinationType, this.destinationName, this.url,
                    this.scheme, this.host, this.port, this.path, this.method, this.queryParams,
                    this.headerParams, this.userName, this.password, this.message);
            return customWebhookMessage;
        }
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() { return method; }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getHeaderParams() {
        return headerParams;
    }

}