package com.amazon.elasticsearch.notification.factory;

import com.amazon.elasticsearch.notification.message.BaseMessage;
import com.amazon.elasticsearch.notification.response.BaseResponse;

/**
 * Interface which enables to plug in multiple notification Channel Factories.
 *
 * @param <T>
 */
public interface ChannelFactory<T extends BaseMessage> {
    public BaseResponse publish(T message);

    public Object getClient(T message);
}
