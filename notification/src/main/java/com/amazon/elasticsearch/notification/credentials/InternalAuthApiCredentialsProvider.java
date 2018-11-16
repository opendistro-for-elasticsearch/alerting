package com.amazon.elasticsearch.notification.credentials;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import org.elasticsearch.common.unit.TimeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * This classes fetches credentials to assume role by making AWS ES internal service call
 */
class InternalAuthApiCredentialsProvider implements AWSCredentialsProvider {

    public static final  Map<String, String> POLICY_TYPES;
    static {
        POLICY_TYPES = new HashMap<String, String>();
        POLICY_TYPES.put("ASSUME_ROLE", "AR");
    }
    private final InternalAuthCredentialsClient internalApiCredentialsClient;
    private final String policyType;
    private AWSCredentials awsCredentials;
    private long expiryTimestamp;

    InternalAuthApiCredentialsProvider(InternalAuthCredentialsClient internalApiCredentialsClient, String policyType) {
        this.internalApiCredentialsClient = internalApiCredentialsClient;
        this.policyType = policyType;
    }

    /**
     * Fetches credentials. It refreshes the credentials if expired
     *
     * @return AWSCredentials
     */
    @Override
    public AWSCredentials getCredentials() {
        if (credentialsHaveExpired()) {
            refresh();
        }

        return awsCredentials;
    }

    /**
     * Refreshes credentials
     */
    @Override
    public synchronized void refresh() {
        if (!credentialsHaveExpired()) {
            return;
        }

        InternalAwsCredentials apiCredentials = internalApiCredentialsClient.getAwsCredentials(policyType);

        if (apiCredentials == null) {
            resetCredentials();
        } else {
            this.awsCredentials = new BasicSessionCredentials(
                    apiCredentials.getAccessKey(), apiCredentials.getSecretKey(), apiCredentials.getSessionToken()
            );
            // subtracting 10 seconds to give the buffer for the requests handled on the boundary
            this.expiryTimestamp = apiCredentials.getExpiry() - TimeValue.timeValueSeconds(10).millis();
        }
    }

    /**
     * Gets the expiry timestamp of the temporary credentials
     *
     * @return expiry timestamp
     */
    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    private boolean credentialsHaveExpired() {
        return awsCredentials == null || System.currentTimeMillis() > expiryTimestamp;
    }

    private void resetCredentials() {
        this.awsCredentials = null;
        this.expiryTimestamp = 0;
    }
}
