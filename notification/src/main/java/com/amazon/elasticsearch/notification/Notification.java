package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.factory.DestinationFactory;
import com.amazon.elasticsearch.notification.factory.DestinationFactoryProvider;
import com.amazon.elasticsearch.notification.message.BaseMessage;
import com.amazon.elasticsearch.notification.response.BaseResponse;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This is a client facing Notification class to publish the messages
 * to the Notification channel like SNS, Email, Webhooks etc
 */
public class Notification {
    private DestinationFactoryProvider factoryProvider;

    private BaseResponse run(BaseMessage notificationMessage) throws Exception {
        DestinationFactory destinationFactory = DestinationFactoryProvider.getFactory(notificationMessage.getChannelType());
        return destinationFactory.publish(notificationMessage);
    }

    /**
     * Publishes the notification message to the corresponding notification
     * channel
     *
     * @param notificationMessage
     * @return BaseResponse
     */
    public static BaseResponse publish(BaseMessage notificationMessage) {
            return AccessController.doPrivileged((PrivilegedAction<BaseResponse>) () -> {
                DestinationFactory destinationFactory = DestinationFactoryProvider.getFactory(notificationMessage.getChannelType());
                return destinationFactory.publish(notificationMessage);
            });
    }
}

