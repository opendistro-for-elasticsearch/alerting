package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.util.Util;
import org.junit.Test;

import static org.junit.Assert.*;

public class UtilTest {

    @Test
    public void testValidSNSTopicArn() {
        String topicArn = "arn:aws:sns:us-west-2:475313751589:test-notification";
        assertTrue("topic arn should be valid", Util.isValidSNSArn(topicArn));
        topicArn = "arn:aws-cn:sns:us-west-2:475313751589:test-notification";
        assertTrue("topic arn should be valid", Util.isValidSNSArn(topicArn));
    }

    @Test
    public void testInvalidSNSTopicArn() {
        String topicArn = "arn:aws:sns1:us-west-2:475313751589:test-notification";
        assertFalse("topic arn should be Invalid", Util.isValidSNSArn(topicArn));
    }

    @Test
    public void testIAMRoleArn() {
        String roleArn = "arn:aws:iam::853806060000:role/domain/abc";
        assertTrue("IAM role arn should be valid", Util.isValidIAMArn(roleArn));
    }

    @Test
    public void testInvalidIAMRoleArn() {
        String roleArn = "arn:aws:iam::85380606000000000:role/domain/010-asdf";
        assertFalse("IAM role arn should be Invalid", Util.isValidIAMArn(roleArn));
    }

    @Test
    public void testGetRegion() {
        String topicArn = "arn:aws:sns:us-west-2:475313751589:test-notification";
        assertEquals(Util.getRegion(topicArn), "us-west-2");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGetRegion() {
        String topicArn = "arn:aws:abs:us-west-2:475313751589:test-notification";
        assertEquals(Util.getRegion(topicArn), "us-west-2");
    }
}
