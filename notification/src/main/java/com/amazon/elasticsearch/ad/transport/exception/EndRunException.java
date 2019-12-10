package com.amazon.elasticsearch.ad.transport.exception;

/**
 * Exception for failures that might impact the customer.
 *
 */
public class EndRunException extends ClientException {
    private boolean endNow;

    public EndRunException(String anomalyDetectorId, String message, boolean endNow) {
        super(anomalyDetectorId, message);
        this.endNow = endNow;
    }

    public EndRunException(String anomalyDetectorId, String message, Throwable throwable, boolean endNow) {
        super(anomalyDetectorId, message, throwable);
        this.endNow = endNow;
    }

    /**
     * @return true for "unrecoverable issue". We want to terminate the detector run immediately.
     *         false for "maybe unrecoverable issue but worth retrying a few more times." We want
     *          to wait for a few more times on different requests before terminating the detector run.
     */
    public boolean isEndNow() {
        return endNow;
    }
}
