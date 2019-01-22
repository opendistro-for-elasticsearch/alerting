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

import com.amazon.opendistro.notification.message.SNSMessage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.AmazonSNSException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class NotificationTest {
    private AmazonSNSClient amazonSNSClient = EasyMock.createMock(AmazonSNSClient.class);

    @BeforeClass
    public static void setUp() {
        System.setProperty("aws.accessKeyId", "dummyAccessKey");
        System.setProperty("aws.secretKey", "dummySecretKey");
    }

    @Test(expected = AmazonSNSException.class)
    @Ignore("Fails in sandboxed builder fleet: https://build.amazon.com/log?btmTaskId=2603099800")
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
