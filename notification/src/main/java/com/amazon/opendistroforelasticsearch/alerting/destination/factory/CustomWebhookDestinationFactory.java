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

package com.amazon.opendistroforelasticsearch.alerting.destination.factory;

import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationHttpClient;
import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationHttpClientPool;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.CustomWebhookMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationHttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.rest.RestStatus;

/**
 * This class handles the client responsible for submitting the messages to custom webhook destination.
 */
public class CustomWebhookDestinationFactory implements DestinationFactory<CustomWebhookMessage, DestinationHttpClient>{

    private static final Logger logger = LogManager.getLogger(CustomWebhookDestinationFactory.class);

    private DestinationHttpClient destinationHttpClient;

    public CustomWebhookDestinationFactory() {
        this.destinationHttpClient = DestinationHttpClientPool.getHttpClient();
    }

    @Override
    public DestinationHttpResponse publish(CustomWebhookMessage message) {
        try {
            String response = getClient(message).execute(message);
            return new DestinationHttpResponse.Builder().withStatusCode(RestStatus.OK.getStatus()).withResponseContent(response).build();
        } catch (Exception ex) {
            logger.error("Exception publishing Message: " + message.toString(), ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public DestinationHttpClient getClient(CustomWebhookMessage message) {
        return destinationHttpClient;
    }

    /*
     *  This function can be used to mock the client for unit test
     */
    public void setClient(DestinationHttpClient client) {
        this.destinationHttpClient = client;
    }

}