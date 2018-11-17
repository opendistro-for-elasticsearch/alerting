package com.amazon.elasticsearch.notification.credentials;

import org.apache.logging.log4j.Logger;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.unit.TimeValue;

import java.io.IOException;

/**
 * This class handles client configuration for AWS ES internal service calls
 */
public class InternalAuthCredentialsClient {

    private static final Logger logger = Loggers.getLogger(InternalAuthCredentialsClient.class);

    private static final int TIMEOUT_MILLISECONDS = (int)TimeValue.timeValueSeconds(5).millis();
    private static final int SOCKET_TIMEOUT_MILLISECONDS = (int)TimeValue.timeValueSeconds(70).millis();

    private final static CloseableHttpClient HTTP_CLIENT;

    static {
        HTTP_CLIENT = createHttpClient();
    }

    public InternalAwsCredentials getAwsCredentials(String policyType) {
        try {
            InternalAwsCredentials internalAwsCredentials = getInternalAwsCredentials(policyType);

            return !internalAwsCredentials.isEmpty() ? internalAwsCredentials : null;

        } catch (IOException e) {
            logger.error("Could not fetch AWS credentials", e);
            return null;
        }
    }

    private static CloseableHttpClient createHttpClient() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MILLISECONDS)
                .setConnectionRequestTimeout(TIMEOUT_MILLISECONDS)
                .setSocketTimeout(SOCKET_TIMEOUT_MILLISECONDS)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(5);

        return HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setConnectionManager(connectionManager)
                .setRetryHandler(new DefaultHttpRequestRetryHandler())
                .build();
    }

    private InternalAwsCredentials getInternalAwsCredentials(String policyType) throws IOException {
        return (new InternalAuthCredentialsApiRequest(HTTP_CLIENT, policyType)).execute();
    }
}
