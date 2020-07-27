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

import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationEmailClient;
import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationEmailClientPool;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.EmailMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationEmailResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class handles the client responsible for submitting the messages to the Email destination.
 */
public class EmailDestinationFactory implements DestinationFactory<EmailMessage, DestinationEmailClient>{

    private DestinationEmailClient destinationEmailClient;

    private static final Logger logger = LogManager.getLogger(EmailDestinationFactory.class);

    public EmailDestinationFactory() {
        this.destinationEmailClient = DestinationEmailClientPool.getEmailClient();
    }

    @Override
    public DestinationEmailResponse publish(EmailMessage message) {
        try {
            String response = getClient(message).execute(message);
            int status = response.equals("Sent") ? 0 : 1;
            return new DestinationEmailResponse.Builder().withStatusCode(status).withResponseContent(response).build();
        } catch (Exception ex) {
            logger.error("Exception publishing Message: " + message.toString(), ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public DestinationEmailClient getClient(EmailMessage message) {
        return destinationEmailClient;
    }

    /*
     *  This function can be used to mock the client for unit test
     */
    public void setClient(DestinationEmailClient client) {
        this.destinationEmailClient = client;
    }

}
