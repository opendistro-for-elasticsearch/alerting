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

package com.amazon.opendistro.notification.factory;

import com.amazon.opendistro.notification.credentials.CredentialsProviderFactory;
import com.amazon.opendistro.notification.credentials.ExpirableCredentialsProviderFactory;
import com.amazon.opendistro.notification.credentials.InternalAuthCredentialsClient;
import com.amazon.opendistro.notification.credentials.InternalAuthCredentialsClientPool;
import com.amazon.opendistro.notification.message.SNSMessage;
import com.amazon.opendistro.notification.response.SNSResponse;
import com.amazon.opendistro.notification.util.Util;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishResult;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import java.util.HashMap;
import java.util.Map;

/**
 * This class handles the clients responsible for submitting the sns messages.
 * It caches the credentials for the IAM roles, so that it can be reused.
 * <p>
 * This class fetches credentials provider from different sources(based on priority) and uses
 * the first one that works.
 */

final class SNSDestinationFactory implements DestinationFactory<SNSMessage, AmazonSNS> {
    private static final Logger logger = Loggers.getLogger(SNSDestinationFactory.class);

    private final InternalAuthCredentialsClient internalApiCredentialsClient;
    private final CredentialsProviderFactory[] orderedCredentialsProviderSources;

    /*
     * Mapping between IAM roleArn and SNSClientHelper. Each role will have its own credentials.
     */
    Map<String, SNSClientHelper> roleClientMap = new HashMap<>();

    SNSDestinationFactory() {
        this.internalApiCredentialsClient = InternalAuthCredentialsClientPool
                .getInstance()
                .getInternalAuthClient(getClass().getName());
        this.orderedCredentialsProviderSources = getOrderedCredentialsProviderSources();
    }

    /**
     * @param message
     * @return SNSResponse
     */
    @Override
    public SNSResponse publish(SNSMessage message) {
        try {
            AmazonSNS snsClient = getClient(message);
            PublishResult result = snsClient.publish(message.getTopicArn(), message.getMessage());
            logger.info("Message successfully published: " + result.getMessageId());
            return new SNSResponse.Builder().withMessageId(result.getMessageId())
                    .withStatusCode(result.getSdkHttpMetadata().getHttpStatusCode()).build();
        } catch (Exception ex) {
            logger.error("Exception publishing Message: " + message.toString(), ex);
            throw ex;
        }
    }

    /**
     * Fetches the client corresponding to an IAM role
     *
     * @param message sns message
     * @return AmazonSNS AWS SNS client
     */
    @Override
    public AmazonSNS getClient(SNSMessage message) {
        AWSCredentialsProvider credentialsProvider;
        String roleArn = message.getRoleArn();

        if (!roleClientMap.containsKey(roleArn)) {
            credentialsProvider = getProvider(roleArn);
            roleClientMap.put(roleArn, new SNSClientHelper(credentialsProvider));
        }

        AmazonSNS snsClient = roleClientMap.get(roleArn).getSnsClient(Util.getRegion(message.getTopicArn()));
        return snsClient;
    }

    /**
     * @param roleArn
     * @return AWSCredentialsProvider
     * @throws IllegalArgumentException
     */
    public AWSCredentialsProvider getProvider(String roleArn) throws IllegalArgumentException {
        AWSCredentialsProvider credentialsProvider;

        for (CredentialsProviderFactory providerSource : orderedCredentialsProviderSources) {
            credentialsProvider = providerSource.getProvider(roleArn);

            if (credentialsProvider != null) {
                return credentialsProvider;
            }
        }
        // no credential provider present
        return null;
    }

    private CredentialsProviderFactory[] getOrderedCredentialsProviderSources() {
        return new CredentialsProviderFactory[]{
                // currently we are just supporting internal credential provider factory, going forward we would
                // support multiple provider factories. We can mention the order in which the credential provdier
                // can be picked up here
                new ExpirableCredentialsProviderFactory(internalApiCredentialsClient)
        };
    }
}

/**
 * This helper class caches the credentials for a role and creates client
 * for each AWS region based on the topic ARN
 */
class SNSClientHelper {
    private AWSCredentialsProvider credentialsProvider;
    // Map between Region and client
    private Map<String, AmazonSNS> snsClientMap = new HashMap();

    SNSClientHelper(AWSCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    public AmazonSNS getSnsClient(String region) {
        if (!snsClientMap.containsKey(region)) {
            AmazonSNS snsClient = AmazonSNSClientBuilder.standard()
                    .withRegion(region)
                    .withCredentials(credentialsProvider).build();
            snsClientMap.put(region, snsClient);
        }
        return snsClientMap.get(region);
    }
}
