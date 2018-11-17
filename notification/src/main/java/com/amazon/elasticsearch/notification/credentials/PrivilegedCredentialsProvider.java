package com.amazon.elasticsearch.notification.credentials;

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
