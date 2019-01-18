package com.amazon.elasticsearch.notification.factory;

import com.amazon.elasticsearch.notification.message.BaseMessage;
import com.amazon.elasticsearch.notification.message.DestinationType;
import com.amazon.elasticsearch.notification.response.BaseResponse;

/**
 * Interface which enables to plug in multiple notification Channel Factories.
 *
 * @param <T> message object of type [{@link DestinationType}]
 * @param <Y> client to publish above message
 */
public interface DestinationFactory<T extends BaseMessage, Y> {
    public BaseResponse publish(T message);

    public Y getClient(T message);
}
