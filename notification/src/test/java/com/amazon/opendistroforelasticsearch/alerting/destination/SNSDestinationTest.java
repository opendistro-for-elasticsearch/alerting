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

package com.amazon.opendistroforelasticsearch.alerting.destination;

import com.amazon.opendistroforelasticsearch.alerting.destination.factory.SNSDestinationFactory;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.SNSMessage;
import com.amazonaws.services.sns.AmazonSNS;
import org.elasticsearch.common.settings.SecureString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SNSDestinationTest {

    @Test(expected = IllegalArgumentException.class)
    public void testCreateTopicArnMissingMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                    .withRoleArn("arn:aws:iam::853806060000:role/domain/abc").build();
        } catch (Exception ex) {
            assertEquals("Topic arn is missing/invalid: null", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateIamAccessKeyMissingMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                    .withIAMSecretKey(new SecureString("randomSecretString"))
                    .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        } catch (Exception ex) {
            assertEquals("IAM user access key is missing", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateIamSecretKeyMissingMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                    .withIAMAccessKey(new SecureString("randomAccessString"))
                    .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        } catch (Exception ex) {
            assertEquals("IAM user secret key is missing", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateContentMissingMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms")
                    .withRoleArn("arn:aws:iam::853806060000:role/domain/abc")
                    .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInValidRoleMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                    .withRoleArn("dummyRole")
                    .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        } catch (Exception ex) {
            assertEquals("Role arn is invalid: dummyRole", ex.getMessage());
            throw ex;
        }
    }

    @Test
    public void testValidMessage() {
        SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                .withSubject("dummySubject")
                .withRoleArn("arn:aws:iam::853806060000:role/domain/abc")
                .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        assertEquals("sms", message.getChannelName());
    }

    @Test
    public void testToStringWithRole() {
        SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                .withSubject("dummySubject")
                .withRoleArn("arn:aws:iam::853806060000:role/domain/abc")
                .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        assertEquals("DestinationType: SNS, DestinationName:sms, " +
                "roleArn: arn:aws:iam::853806060000:role/domain/abc, " +
                "TopicArn: arn:aws:sns:us-west-2:475313751589:test-notification, " +
                "Subject: dummySubject, Message: dummyMessage", message.toString());
    }

    @Test
    public void testToStringWithoutRole() {
        SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                .withSubject("dummySubject")
                .withIAMAccessKey(new SecureString("randomAccessString"))
                .withIAMSecretKey(new SecureString("randomSecretString"))
                .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        assertEquals("DestinationType: SNS, DestinationName:sms, AccessKey: randomAccessString, " +
                "TopicArn: arn:aws:sns:us-west-2:475313751589:test-notification, Subject: dummySubject, " +
                "Message: dummyMessage", message.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInValidChannelName() {
        try {
            SNSMessage message = new SNSMessage.Builder("").withMessage("dummyMessage")
                    .withRoleArn("arn:aws:iam::853806060000:role/domain/abc")
                    .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        } catch (Exception ex) {
            assertEquals("Channel name must be defined", ex.getMessage());
            throw ex;
        }
    }

    @Test
    public void testGetClientWithRole() {
        SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                .withSubject("dummySubject")
                .withRoleArn("arn:aws:iam::853806060000:role/domain/abc")
                .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        SNSDestinationFactory snsDestinationFactory = new SNSDestinationFactory();
        AmazonSNS sns = snsDestinationFactory.getClient(message);
        assertNotNull(sns);
    }

    @Test
    public void testGetClientWithoutRole() {
        SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                .withSubject("dummySubject")
                .withIAMAccessKey(new SecureString("randomAccessString"))
                .withIAMSecretKey(new SecureString("randomSecretString"))
                .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        SNSDestinationFactory snsDestinationFactory = new SNSDestinationFactory();
        AmazonSNS sns = snsDestinationFactory.getClient(message);
        assertNotNull(sns);
    }
}

