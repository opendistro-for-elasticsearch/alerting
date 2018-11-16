package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.response.SNSResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SNSResponseTest {

    @Test(expected = IllegalArgumentException.class)
    public void testCreateInvalidResponse() {
        SNSResponse snsResponse = new SNSResponse.Builder().withMessageId("adsfadsfadsfdas").build();
    }

    @Test
    public void testCreateValidResponse() {
        String messageId = "adsfadsfadsfdas";
        SNSResponse snsResponse = new SNSResponse.Builder().withMessageId(messageId).withStatusCode(200)
                .build();
        assertEquals(messageId, snsResponse.getMessageId());
    }
}
