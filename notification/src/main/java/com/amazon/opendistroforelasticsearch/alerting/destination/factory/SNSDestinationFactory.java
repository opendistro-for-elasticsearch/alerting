package com.amazon.opendistroforelasticsearch.alerting.destination.factory;

import com.amazon.opendistroforelasticsearch.alerting.destination.message.SNSMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationResponse;
import com.amazon.opendistroforelasticsearch.alerting.destination.util.Util;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.queryparser.ext.Extensions.Pair;
import org.elasticsearch.common.Strings;

import java.util.HashMap;
import java.util.Map;

public final class SNSDestinationFactory implements DestinationFactory<SNSMessage, AmazonSNS> {
    private static final Logger logger = LogManager.getLogger(SNSDestinationFactory.class);

    private static final Map<Pair<String, String>, AmazonSNS> snsClientMap = new HashMap<>();

    /**
     * Publishes SNS message
     *
     * @param message sns message
     * @return SNSResponse
     */
    @Override
    public DestinationResponse publish(SNSMessage message) {
        AmazonSNS snsClient = getClient(message);
        PublishResult result;
        if (!Strings.isNullOrEmpty(message.getSubject())) {
            result = snsClient.publish(message.getTopicArn(), message.getMessage(), message.getSubject());
        } else {
            result = snsClient.publish(message.getTopicArn(), message.getMessage());
        }
        logger.info("Message successfully published: " + result.getMessageId());
        return new DestinationResponse.Builder().withResponseContent(result.getMessageId())
                .withStatusCode(result.getSdkHttpMetadata().getHttpStatusCode()).build();
    }

    /**
     * Fetches the client corresponding to a topic role
     *
     * @param message sns message
     * @return AmazonSNS AWS SNS client
     */
    @Override
    public AmazonSNS getClient(SNSMessage message) {
        String credKey;
        if (message.getRoleArn() == null) {
            String accessKey = message.getIAMAccessKey().toString();
            String secretKey = message.getIAMSecretKey().toString();
            credKey = String.format("%s %s", accessKey, secretKey);
        } else {
            credKey = message.getRoleArn();
        }
        String region = Util.getRegion(message.getTopicArn());
        Pair<String, String> clientKey = new Pair<>(credKey, region);
        if (!snsClientMap.containsKey(clientKey)) {
            AmazonSNS snsClient = AmazonSNSClientBuilder.standard()
                    .withRegion(region)
                    .withCredentials(getProvider(credKey)).build();
            snsClientMap.put(clientKey, snsClient);
        }

        return snsClientMap.get(clientKey);
    }

    /**
     * Builds the AWSCredentialsProvider
     *
     * @return AWSCredentialsProvider
     * @throws IllegalArgumentException
     */
    public AWSCredentialsProvider getProvider(String credKey) throws IllegalArgumentException {
        if (credKey.contains(" ")) {
            String[] keys = credKey.split(" ");
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(keys[0], keys[1]);
            return new AWSStaticCredentialsProvider(awsCredentials);
        } else {
            return new STSAssumeRoleSessionCredentialsProvider
                    .Builder(credKey, "es-notification-sns-session").build();
        }
    }
}
