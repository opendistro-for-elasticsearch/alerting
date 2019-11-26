package com.amazon.elasticsearch.ad.transport.exception;

/**
 * Base exception for exceptions thrown from Anomaly Detection.
 */
public class AnomalyDetectionException extends RuntimeException {

    private final String anomalyDetectorId;

    /**
     * Constructor with an anomaly detector ID and a message.
     *
     * @param anomalyDetectorId anomaly detector ID
     * @param message message of the exception
     */
    public AnomalyDetectionException(String anomalyDetectorId, String message) {
        super(message);
        this.anomalyDetectorId = anomalyDetectorId;
    }

    public AnomalyDetectionException(String adID, String message, Throwable cause) {
        super(message, cause);
        this.anomalyDetectorId = adID;
    }

    public AnomalyDetectionException(String adID, Throwable cause) {
        super(cause);
        this.anomalyDetectorId = adID;
    }

    /**
     * Returns the ID of the anomaly detector.
     *
     * @return anomaly detector ID
     */
    public String getAnomalyDetectorId() {
        return this.anomalyDetectorId;
    }
}
