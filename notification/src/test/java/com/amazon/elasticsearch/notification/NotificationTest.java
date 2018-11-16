package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.message.SNSMessage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.AmazonSNSException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

public class NotificationTest {
    private AmazonSNSClient amazonSNSClient = EasyMock.createMock(AmazonSNSClient.class);

    @BeforeClass
    public static void setUp() {
        System.setProperty("aws.accessKeyId", "dummyAccessKey");
        System.setProperty("aws.secretKey", "dummySecretKey");
    }

    @Test(expected = AmazonSNSException.class)
    public void testSNSNotification() {
        PublishResult result = new PublishResult();
        result.setMessageId("messageId");
        EasyMock.expect(
                amazonSNSClient.publish(EasyMock.anyObject(PublishRequest.class))).andReturn(
                result);
        EasyMock.replay(amazonSNSClient);

        SNSMessage message = new SNSMessage.Builder("sns").withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification")
                .withRole("arn:aws:iam::853806060000:role/domain/abc").withMessage("Hello").build();
        Notification.publish(message);
    }
}
