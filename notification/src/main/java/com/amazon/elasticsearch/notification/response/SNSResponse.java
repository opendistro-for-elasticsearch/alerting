package com.amazon.elasticsearch.notification.response;

import org.elasticsearch.common.Strings;

/**
 * This class is a place holder for SNS response metadata
 */
public class SNSResponse extends BaseResponse {
    private String messageId;

    private SNSResponse(final String messageId, final Integer statusCode) {
        super(statusCode);
        if (!Strings.hasLength(messageId)) {
            throw new IllegalArgumentException("MessageId is missing in the response");
        }
        this.messageId = messageId;
    }

    public static class Builder {
        private String messageId;
        private Integer statusCode;

        public Builder withMessageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder withStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public SNSResponse build() {
            return new SNSResponse(messageId, statusCode);
        }
    }

    public String getMessageId() {
        return this.messageId;
    }
}
