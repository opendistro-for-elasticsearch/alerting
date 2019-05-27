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

import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationMailClient;
import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationMailClientPool;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.MailMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationMailResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class handles the client responsible for submitting the messages to Mail destination.
 */
public class MailDestinationFactory implements DestinationFactory<MailMessage, DestinationMailClient>{

    private DestinationMailClient destinationMailClient;

    private static final Logger logger = LogManager.getLogger(MailDestinationFactory.class);

    public MailDestinationFactory() {
        this.destinationMailClient = DestinationMailClientPool.getMailClient();
    }

    @Override
    public DestinationMailResponse publish(MailMessage message) {
        try {
            String response = getClient(message).execute(message);
            int status = response == "Sent" ? 0 : 1;
            return new DestinationMailResponse.Builder().withStatusCode(status).withResponseContent(response).build();
        } catch (Exception ex) {
            logger.error("Exception publishing Message: " + message.toString(), ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public DestinationMailClient getClient(MailMessage message) {
        return destinationMailClient;
    }

    /*
     *  This function can be used to mock the client for unit test
     */
    public void setClient(DestinationMailClient client) {
        this.destinationMailClient = client;
    }

}
