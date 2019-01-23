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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.util.EC2MetadataUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.Loggers;

/**
 * Factory class that provides temporary credentials. It refreshes the credentials on demand.
 */
public class ExpirableCredentialsProviderFactory implements CredentialsProviderFactory {

    private static final String STS_ENDPOINT_PREFIX = "sts";
    private static final String ZHY = "cn-northwest-1";
    private static final String BJS = "cn-north-1";

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
    public AwsClientBuilder.EndpointConfiguration getSTSEndpointConfiguration(String region) {
        if (!Strings.isEmpty(region)) {
            try {
                switch (region) {
                    case ZHY:
                        return new AwsClientBuilder.EndpointConfiguration("sts.cn-northwest-1.amazonaws.com.cn", region);
                    case BJS:
                        return new AwsClientBuilder.EndpointConfiguration("sts.cn-north-1.amazonaws.com.cn", region);
                    default:
                        String serviceEndpoint = String.format("%s.%s.amazonaws.com", STS_ENDPOINT_PREFIX, region);
                        return new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region);
                }
            } catch (Exception ex) {
                logger.error("Error fetching STS endpoint information. Defaulting to global");
            }
        }
        logger.info("Region not provided, defaulting to global");
        return new AwsClientBuilder.EndpointConfiguration("sts.amazonaws.com", "us-east-1");
    }
}
