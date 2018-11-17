package com.amazon.elasticsearch.notification.message;

import org.elasticsearch.common.Strings;

/**
 * This class holds the generic parameters required for a
 * message.
 */
public abstract class BaseMessage {

    protected NotificationChannel channelType;
    protected String channelName;

    BaseMessage(final NotificationChannel channelType, final String channelName) {
        if (channelType == null) {
            throw new IllegalArgumentException("Channel type must be defined");
        }
        if (!Strings.hasLength(channelName)) {
            throw new IllegalArgumentException("Channel name must be defined");
        }
        this.channelType = channelType;
        this.channelName = channelName;
    }

    public NotificationChannel getChannelType() {
        return channelType;
    }

    public String getChannelName() {
        return channelName;
    }
}
