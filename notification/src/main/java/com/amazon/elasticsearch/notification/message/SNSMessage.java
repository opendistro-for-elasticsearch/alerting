package com.amazon.elasticsearch.notification.message;

import com.amazon.elasticsearch.notification.util.Util;
import org.elasticsearch.common.Strings;

/**
 * This class holds the content of an SNS message
 */
public class SNSMessage extends BaseMessage {

    private String subject;
    private String message;
    private String roleArn;
    private String topicArn;

    private SNSMessage(final DestinationType destinationType, final String destinationName, final String roleArn,
                       final String topicArn, final String subject, final String message) {

        super(destinationType, destinationName, message);

        if (DestinationType.SNS != destinationType) {
            throw new IllegalArgumentException("Channel Type does not match SNS");
        }

        if (!Strings.hasLength(roleArn) || !Util.isValidIAMArn(roleArn)) {
            throw new IllegalArgumentException("Role arn is missing/invalid: " + roleArn);
        }

        if (!Strings.hasLength(topicArn) || !Util.isValidSNSArn(topicArn)) {
            throw new IllegalArgumentException("Topic arn is missing/invalid: " + topicArn);
        }

        if (!Strings.hasLength(message)) {
            throw new IllegalArgumentException("Message content is missing");
        }

        this.subject = subject;
        this.message = message;
        this.roleArn = roleArn;
        this.topicArn = topicArn;
    }

    @Override
    public String toString() {
        return "DestinationType: " + destinationType + ", DestinationName:" +  destinationName +
                ", RoleARn: " + roleArn + ", TopicArn: " + topicArn + ", Subject: " + subject +
                ", Message: " + message;
    }

    public static class Builder {
        private String subject;
        private String message;
        private String roleArn;
        private String topicArn;
        private DestinationType destinationType;
        private String destinationName;

        public Builder(String destinationName) {
            this.destinationName = destinationName;
            this.destinationType = DestinationType.SNS;
        }

        public Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder withRole(String roleArn) {
            this.roleArn = roleArn;
            return this;
        }

        public Builder withTopicArn(String topicArn) {
            this.topicArn = topicArn;
            return this;
        }

        public SNSMessage build() {
            SNSMessage snsMessage = new SNSMessage(this.destinationType, this.destinationName, this.roleArn, this.topicArn,
                    this.subject, this.message);

            return snsMessage;
        }
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getTopicArn() {
        return topicArn;
    }

}
