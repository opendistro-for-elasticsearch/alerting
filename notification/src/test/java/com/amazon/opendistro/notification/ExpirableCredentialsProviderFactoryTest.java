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

package com.amazon.opendistro.notification;

import com.amazon.opendistro.notification.credentials.ExpirableCredentialsProviderFactory;
import com.amazon.opendistro.notification.credentials.InternalAuthCredentialsClient;
import com.amazon.opendistro.notification.credentials.InternalAwsCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import org.elasticsearch.common.unit.TimeValue;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExpirableCredentialsProviderFactoryTest {

    @Test(expected = AWSSecurityTokenServiceException.class)
    @Ignore("Fails in brazil build fleet: https://build.amazon.com/log?btmTaskId=2603099800")
    public void testGetProvider() {
        String roleArn = "arn:aws:iam::853806060000:role/domain/abc";

        InternalAuthCredentialsClient mockAuthClient = mock(InternalAuthCredentialsClient.class);
        InternalAwsCredentials mockInternalCreds = new InternalAwsCredentials();
        mockInternalCreds.setAccessKey("dummyAccessKey");
        mockInternalCreds.setSecretKey("dummySecretKey");
        mockInternalCreds.setExpiry(System.currentTimeMillis() + TimeValue.timeValueMinutes(5).millis());
        when(mockAuthClient.getAwsCredentials("AR")).thenReturn(mockInternalCreds);

        ExpirableCredentialsProviderFactory expirableCredentialsProviderFactory = new ExpirableCredentialsProviderFactory(mockAuthClient);
        AWSCredentialsProvider credentialsProvider = expirableCredentialsProviderFactory.getProvider(roleArn);
        credentialsProvider.getCredentials();
    }

    @Test
    public void testSTSEndpointConfiguration() {
        ExpirableCredentialsProviderFactory expirableCredentialsProviderFactory = new ExpirableCredentialsProviderFactory(null);
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = expirableCredentialsProviderFactory.getSTSEndpointConfiguration("us-west-2");
        assertEquals("sts.us-west-2.amazonaws.com", endpointConfiguration.getServiceEndpoint());
        assertEquals("us-west-2", endpointConfiguration.getSigningRegion());

        // China region
        endpointConfiguration = expirableCredentialsProviderFactory.getSTSEndpointConfiguration("cn-north-1");
        assertEquals("sts.cn-north-1.amazonaws.com.cn", endpointConfiguration.getServiceEndpoint());
        assertEquals("cn-north-1", endpointConfiguration.getSigningRegion());

        //gov region
        endpointConfiguration = expirableCredentialsProviderFactory.getSTSEndpointConfiguration("us-gov-west-1");
        assertEquals("sts.us-gov-west-1.amazonaws.com", endpointConfiguration.getServiceEndpoint());
        assertEquals("us-gov-west-1", endpointConfiguration.getSigningRegion());

        //default case
        endpointConfiguration = expirableCredentialsProviderFactory.getSTSEndpointConfiguration("");
        assertEquals("sts.amazonaws.com", endpointConfiguration.getServiceEndpoint());
        assertEquals("us-east-1", endpointConfiguration.getSigningRegion());
    }
}
