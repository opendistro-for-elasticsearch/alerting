package com.amazon.elasticsearch.notification.message;

import org.elasticsearch.common.Strings;

/**
 * This class holds the content of an Slack message
 */
public class SlackMessage extends BaseMessage {

    private String subject;
    private String message;

    private SlackMessage(final DestinationType destinationType, final String destinationName, final String url,
                         final String subject, final String message) {

        super(destinationType, destinationName, message, url);

        if (DestinationType.SLACK != destinationType) {
            throw new IllegalArgumentException("Channel Type does not match Slack");
        }

        if (Strings.isNullOrEmpty(url)) { // add URL validation
            throw new IllegalArgumentException("Fully qualified URL is missing/invalid: " + url);
        }

        if (Strings.isNullOrEmpty(message)) {
            throw new IllegalArgumentException("Message content is missing");
        }

        this.subject = subject;
        this.message = message;
    }

    @Override
    public String toString() {
        return "DestinationType: " + destinationType + ", DestinationName:" +  destinationName +
                ", Url: " + url + ", Message: " + message;
    }

    public static class Builder {
        private String subject;
        private String message;
        private DestinationType destinationType;
        private String destinationName;
        private String url;

        public Builder(String channelName) {
            this.destinationName = channelName;
            this.destinationType = DestinationType.SLACK;
        }

        public SlackMessage.Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public SlackMessage.Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public SlackMessage.Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public SlackMessage build() {
            SlackMessage slackMessage = new SlackMessage(this.destinationType, this.destinationName, this.url,
                    this.subject, this.message);

            return slackMessage;
        }
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getUrl() {
        return url;
    }
}
