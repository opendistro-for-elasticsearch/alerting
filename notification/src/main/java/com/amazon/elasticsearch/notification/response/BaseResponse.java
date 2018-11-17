package com.amazon.elasticsearch.notification.response;

/**
 * This class holds the generic response attributes
 */
public abstract class BaseResponse {
    protected int statusCode = -1;

    BaseResponse(final int statusCode) {
        if (statusCode == -1) {
            throw new IllegalArgumentException("status code is invalid");
        }
    }

    public int getStatusCode() {
        return statusCode;
    }
}
