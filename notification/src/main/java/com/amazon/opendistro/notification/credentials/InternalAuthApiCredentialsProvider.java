/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistro.notification.credentials;

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
