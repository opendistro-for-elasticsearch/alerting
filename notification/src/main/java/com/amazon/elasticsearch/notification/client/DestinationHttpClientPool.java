package com.amazon.elasticsearch.notification.client;

/**
 *  This class provides Client to the relevant destinations
 */
public final class DestinationHttpClientPool {

    private static final DestinationHttpClient httpClient = new DestinationHttpClient();

    private DestinationHttpClientPool() { }

    public static DestinationHttpClient getHttpClient() {
        return httpClient;
    }
}
