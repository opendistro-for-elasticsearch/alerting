package com.amazon.elasticsearch.notification.credentials;

import java.util.Map;
import java.util.HashMap;


/**
 * This class fetches credentials provider from different sources(based on priority) and uses the first one that works.
 */
public final class InternalAuthCredentialsClientPool {

    private static final InternalAuthCredentialsClientPool instance = new InternalAuthCredentialsClientPool();

    private Map<String, InternalAuthCredentialsClient> clientPool;

    public static InternalAuthCredentialsClientPool getInstance() {
        return instance;
    }

    public synchronized InternalAuthCredentialsClient getInternalAuthClient(String factoryName) {
        if (clientPool.containsKey(factoryName)) {
            return clientPool.get(factoryName);
        }

        return newClient(factoryName);
    }

    private InternalAuthCredentialsClientPool() {
        this.clientPool = new HashMap<String, InternalAuthCredentialsClient>();
    }

    private InternalAuthCredentialsClient newClient(String factoryName) {
        InternalAuthCredentialsClient client = new InternalAuthCredentialsClient();
        clientPool.put(factoryName, client);
        return client;
    }
}
