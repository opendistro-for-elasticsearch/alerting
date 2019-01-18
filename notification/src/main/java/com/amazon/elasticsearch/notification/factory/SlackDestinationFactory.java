package com.amazon.elasticsearch.notification.factory;

import com.amazon.elasticsearch.notification.client.DestinationHttpClient;
import com.amazon.elasticsearch.notification.client.DestinationHttpClientPool;
import com.amazon.elasticsearch.notification.message.SlackMessage;
import com.amazon.elasticsearch.notification.response.DestinationHttpResponse;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.rest.RestStatus;

/**
 * This class handles the client responsible for submitting the messages to Slack destination.
 */
public class SlackDestinationFactory implements DestinationFactory<SlackMessage, DestinationHttpClient>{

    private DestinationHttpClient destinationHttpClient;

    private static final Logger logger = Loggers.getLogger(SlackDestinationFactory.class);

    public SlackDestinationFactory() {
        this.destinationHttpClient = DestinationHttpClientPool.getHttpClient();
    }

    @Override
    public DestinationHttpResponse publish(SlackMessage message) {
        try {
            String response = getClient(message).execute(message);
            return new DestinationHttpResponse.Builder().withStatusCode(RestStatus.OK.getStatus()).withResponseString(response).build();
        } catch (Exception ex) {
            logger.error("Exception publishing Message: " + message.toString(), ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public DestinationHttpClient getClient(SlackMessage message) {
        return destinationHttpClient;
    }

    /*
     *  This function can be used to mock the client for unit test
     */
    public void setClient(DestinationHttpClient client) {
        this.destinationHttpClient = client;
    }

}
