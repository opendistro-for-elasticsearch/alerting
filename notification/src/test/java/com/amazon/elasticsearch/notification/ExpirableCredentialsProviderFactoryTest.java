package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.credentials.ExpirableCredentialsProviderFactory;
import com.amazon.elasticsearch.notification.credentials.InternalAuthCredentialsClient;
import com.amazon.elasticsearch.notification.credentials.InternalAwsCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import org.elasticsearch.common.unit.TimeValue;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ExpirableCredentialsProviderFactoryTest {

    @Test(expected = AWSSecurityTokenServiceException.class)
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
}
