package com.amazon.elasticsearch.notification.message;

import org.elasticsearch.common.Strings;

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

}
