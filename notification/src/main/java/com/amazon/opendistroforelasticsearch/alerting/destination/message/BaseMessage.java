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

import org.apache.http.client.utils.URIBuilder;
import org.elasticsearch.common.Strings;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * This class holds the generic parameters required for a
 * message.
 */
public abstract class BaseMessage {

    protected DestinationType destinationType;
    protected String destinationName;
    protected String url;
    private String content;

    BaseMessage(final DestinationType destinationType, final String destinationName, final String content) {
        if (destinationType == null) {
            throw new IllegalArgumentException("Channel type must be defined");
        }
        if (!Strings.hasLength(destinationName)) {
            throw new IllegalArgumentException("Channel name must be defined");
        }
        this.destinationType = destinationType;
        this.destinationName = destinationName;
        this.content = content;
    }

    BaseMessage(final DestinationType destinationType, final String destinationName,
                final String content, final String url) {
        this(destinationType, destinationName, content);
        if (url == null) {
            throw new IllegalArgumentException("url is invalid or empty");
        }
        this.url = url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
    public DestinationType getChannelType() {
        return destinationType;
    }

    public String getChannelName() {
        return destinationName;
    }

    public String getMessageContent() {
        return content;
    }

    public String getUrl() {
        return url;
    }

    public URI getUri() {
        return buildUri(getUrl().trim(), null, null, -1, null, null);
    }

    protected URI buildUri(String endpoint, String scheme, String host,
                         int port, String path, Map<String, String> queryParams) {
        try {
            if(Strings.isNullOrEmpty(endpoint)) {
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
            throw new IllegalStateException("Error creating URI");
        }
    }

}
