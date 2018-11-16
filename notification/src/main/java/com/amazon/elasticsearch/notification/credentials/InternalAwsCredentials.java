package com.amazon.elasticsearch.notification.credentials;

/**
 * This class is a  placeholder for credentials
 */
public class InternalAwsCredentials {

    private String accessKey;
    private String secretKey;
    private String sessionToken;
    private long expiry;

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public long getExpiry() {
        return expiry;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public void setExpiry(long expiryTimestamp) {
        this.expiry = expiryTimestamp;
    }

    public boolean isEmpty() {
        return accessKey == null || secretKey == null || sessionToken == null;
    }
}
