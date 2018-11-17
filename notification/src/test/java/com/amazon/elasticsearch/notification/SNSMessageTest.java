package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.message.SNSMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SNSMessageTest {

    @Test(expected = IllegalArgumentException.class)
    public void testCreateRoleArnMissingMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage").build();
        } catch (Exception ex) {
            assertEquals("Role arn is missing/invalid: null", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateTopicArnMissingMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                    .withRole("arn:aws:iam::853806060000:role/domain/abc").build();
        } catch (Exception ex) {
            assertEquals("Topic arn is missing/invalid: null", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateContentMissingMessage() {
        try {
            SNSMessage message = new SNSMessage.Builder("sms")
                    .withRole("arn:aws:iam::853806060000:role/domain/abc")
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
                    .withRole("dummyRole")
                    .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        } catch (Exception ex) {
            assertEquals("Role arn is missing/invalid: dummyRole", ex.getMessage());
            throw ex;
        }
    }

    @Test
    public void testValidMessage() {
        SNSMessage message = new SNSMessage.Builder("sms").withMessage("dummyMessage")
                .withRole("arn:aws:iam::853806060000:role/domain/abc")
                .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        assertEquals("sms", message.getChannelName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInValidChannelName() {
        try {
            SNSMessage message = new SNSMessage.Builder("").withMessage("dummyMessage")
                    .withRole("arn:aws:iam::853806060000:role/domain/abc")
                    .withTopicArn("arn:aws:sns:us-west-2:475313751589:test-notification").build();
        } catch (Exception ex) {
            assertEquals("Channel name must be defined", ex.getMessage());
            throw ex;
        }
    }
}

