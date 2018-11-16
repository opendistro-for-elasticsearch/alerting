package com.amazon.elasticsearch.notification.factory;

import com.amazon.elasticsearch.notification.message.NotificationChannel;

import java.util.HashMap;
import java.util.Map;

/*
 * This class helps in fetching the right Channel Factory based on the
 * type of the channel.
 * A channel could be SNS, Email, Webhook etc
 */
public class ChannelFactoryProvider {

    private static Map<NotificationChannel, ChannelFactory> channelFactoryMap = new HashMap<>();

    static {
        channelFactoryMap.put(NotificationChannel.SNS, new SNSChannelFactory());
    }

    /**
     * Fetches the right channel factory based on the type of the channel
     *
     * @param channelType
     * @return ChannelFactory
     */
    public static ChannelFactory getFactory(NotificationChannel channelType) {
        if (!channelFactoryMap.containsKey(channelType)) {
            throw new IllegalArgumentException("Invalid channel type");
        }
        return channelFactoryMap.get(channelType);
    }
}
