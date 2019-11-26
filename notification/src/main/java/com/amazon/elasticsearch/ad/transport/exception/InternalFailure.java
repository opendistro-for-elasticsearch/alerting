package com.amazon.elasticsearch.ad.transport.exception;

/**
 * Exception for root cause unknown failure. Maybe transient. Client can continue the detector running.
 *
 */
public class InternalFailure extends ClientException {

    public InternalFailure(String anomalyDetectorId, String message) {
        super(anomalyDetectorId, message);
    }

    public InternalFailure(String anomalyDetectorId, String message, Throwable cause) {
        super(anomalyDetectorId, message, cause);
    }

    public InternalFailure(String anomalyDetectorId, Throwable cause) {
        super(anomalyDetectorId, cause);
    }

    public InternalFailure(AnomalyDetectionException cause) {
        super(cause.getAnomalyDetectorId(), cause);
    }
}
