package com.amazon.elasticsearch.notification.response;

import org.elasticsearch.common.Strings;

/**
 * This class is a place holder for http response metadata
 */
public class DestinationHttpResponse extends BaseResponse {

    private String responseString;

    private DestinationHttpResponse(final String responseString, final int statusCode) {
        super(statusCode);
        if (Strings.isNullOrEmpty(responseString)) {
            throw new IllegalArgumentException("Response is missing");
        }
        this.responseString = responseString;
    }

    public static class Builder {
        private String responseString;
        private Integer statusCode = null;

        public DestinationHttpResponse.Builder withResponseString(String responseString) {
            this.responseString = responseString;
            return this;
        }

        public DestinationHttpResponse.Builder withStatusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public DestinationHttpResponse build() {
            return new DestinationHttpResponse(responseString, statusCode);
        }
    }

    public String getResponseString() {
        return this.responseString;
    }
}
