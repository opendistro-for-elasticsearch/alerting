package com.amazon.elasticsearch.notification.credentials;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.util.EC2MetadataUtils;
import jdk.nashorn.api.tree.CatchTree;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

/**
 * Factory class that provides temporary credentials. It refreshes the credentials on demand.
 */
public class ExpirableCredentialsProviderFactory implements CredentialsProviderFactory {

    private static final String SNS_ENDPOINT_PREFIX = "sns";

    public ExpirableCredentialsProviderFactory(InternalAuthCredentialsClient internalAuthCredentialsClient) {
        this.internalAuthCredentialsClient = internalAuthCredentialsClient;
    }

    /**
     * Provide expirable credentials.
     *
     * @param roleArn IAM role arn
     * @return AWSCredentialsProvider which holds the credentials.
     */
    @Override
    public AWSCredentialsProvider getProvider(String roleArn) {
        return getExpirableCredentialsProvider(roleArn);
    }

    private static final Logger logger = Loggers.getLogger(ExpirableCredentialsProviderFactory.class);

    private final InternalAuthCredentialsClient internalAuthCredentialsClient;

    private AWSCredentialsProvider getExpirableCredentialsProvider(String roleArn) {
        return findStsAssumeRoleCredentialsProvider(roleArn);
    }

    private AWSCredentialsProvider findStsAssumeRoleCredentialsProvider(String roleArn) {
        AWSCredentialsProvider assumeRoleApiCredentialsProvider = getAssumeRoleApiCredentialsProvider();

        if (assumeRoleApiCredentialsProvider != null) {
            logger.info("Fetching credentials from STS for assumed role");
            return getStsAssumeCustomerRoleProvider(assumeRoleApiCredentialsProvider, roleArn);
        }
        logger.info("Coud not fetch credentials from internal service to assume role");
        return null;
    }

    private AWSCredentialsProvider getAssumeRoleApiCredentialsProvider() {
        InternalAuthApiCredentialsProvider internalAuthApiCredentialsProvider = new InternalAuthApiCredentialsProvider(
                internalAuthCredentialsClient, InternalAuthApiCredentialsProvider.POLICY_TYPES.get("ASSUME_ROLE"));

        return internalAuthApiCredentialsProvider.getCredentials() != null ? internalAuthApiCredentialsProvider : null;
    }

    private AWSCredentialsProvider getStsAssumeCustomerRoleProvider(AWSCredentialsProvider apiCredentialsProvider, String roleArn) {
        String region = "us-east-1";

        try {
            region = EC2MetadataUtils.getEC2InstanceRegion();
        } catch(Exception ex) {
            logger.info("Exception occured while fetching the region info from EC2 metadata. Defaulting to us-east-1");
        }

        AWSSecurityTokenServiceClientBuilder stsClientBuilder = AWSSecurityTokenServiceClientBuilder.standard()
                .withCredentials(apiCredentialsProvider)
                .withEndpointConfiguration(getSTSEndpointConfiguration(region));
        AWSSecurityTokenService stsClient = stsClientBuilder.build();
        STSAssumeRoleSessionCredentialsProvider.Builder providerBuilder = new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, "alerting-notification")
                .withStsClient(stsClient);
        return new PrivilegedCredentialsProvider(providerBuilder.build());
    }

    /**
     * STS global endpoint works for all regions except the isolated ones.
     * Add the region to endpoint mapping if global endpoint is not supported.
     */
    private AwsClientBuilder.EndpointConfiguration getSTSEndpointConfiguration(String region) {
        if (region != null) {
            try {
                Region awsRegion = RegionUtils.getRegion(region);
                String stsServiceEndPoint = awsRegion.getServiceEndpoint(SNS_ENDPOINT_PREFIX);
                return new AwsClientBuilder.EndpointConfiguration(stsServiceEndPoint, region);
            } catch (Exception ex) {
                logger.error("Error fetching SNS endpoint information. Defaulting to global");
            }
        }
        logger.info("Region not provided, defaulting to global");
        return new AwsClientBuilder.EndpointConfiguration("sts.amazonaws.com", "us-east-1");
    }
}
