package com.amazon.elasticsearch.ad.transport.exception;

/**
 * All exception visible to AD transport layer's client is under ClientVisible.
 *
 */
public class ClientException extends AnomalyDetectionException {

    public ClientException(String anomalyDetectorId, String message) {
        super(anomalyDetectorId, message);
    }

    public ClientException(String anomalyDetectorId, String message, Throwable throwable) {
        super(anomalyDetectorId, message, throwable);
    }

    public ClientException(String anomalyDetectorId, Throwable cause) {
        super(anomalyDetectorId, cause);
    }
}
