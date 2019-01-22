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

/**
 * This class helps in fetching the credentials by making socket connections in
 * privileged mode.
 */
public class PrivilegedCredentialsProvider implements AWSCredentialsProvider {

    private final AWSCredentialsProvider credentials;

    PrivilegedCredentialsProvider(AWSCredentialsProvider delegate) {
        this.credentials = delegate;
    }

    @Override
    public AWSCredentials getCredentials() {
        return SocketAccess.doPrivileged(credentials::getCredentials);
    }

    @Override
    public void refresh() {
        SocketAccess.doPrivilegedVoid(credentials::refresh);
    }

    public AWSCredentialsProvider wrappedProvider() {
        return credentials;
    }
}
