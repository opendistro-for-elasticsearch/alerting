package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.client.DestinationHttpClient;
import com.amazon.elasticsearch.notification.factory.ChimeDestinationFactory;
import com.amazon.elasticsearch.notification.factory.DestinationFactoryProvider;
import com.amazon.elasticsearch.notification.message.BaseMessage;
import com.amazon.elasticsearch.notification.message.ChimeMessage;
import com.amazon.elasticsearch.notification.message.DestinationType;
import com.amazon.elasticsearch.notification.response.DestinationHttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChimeDestinationTest {

    @Test
    public void testChimeMessage() throws Exception {
        CloseableHttpClient mockHttpClient = EasyMock.createMock(CloseableHttpClient.class);

        DestinationHttpResponse expectedChimeResponse = new DestinationHttpResponse.Builder().withResponseString("{}").withStatusCode(200).build();
        CloseableHttpResponse httpResponse = EasyMock.createMock(CloseableHttpResponse.class);
        EasyMock.expect(mockHttpClient.execute(EasyMock.anyObject(HttpPost.class))).andReturn(httpResponse);

        BasicStatusLine mockStatusLine = EasyMock.createMock(BasicStatusLine.class);

        EasyMock.expect(httpResponse.getStatusLine()).andReturn(mockStatusLine);
        EasyMock.expect(httpResponse.getEntity()).andReturn(null);
        EasyMock.expect(mockStatusLine.getStatusCode()).andReturn(200);
        EasyMock.replay(mockHttpClient);
        EasyMock.replay(httpResponse);
        EasyMock.replay(mockStatusLine);

        DestinationHttpClient httpClient = new DestinationHttpClient();
        httpClient.setHttpClient(mockHttpClient);
        ChimeDestinationFactory chimeDestinationFactory = new ChimeDestinationFactory();
        chimeDestinationFactory.setClient(httpClient);

        DestinationFactoryProvider.setFactory(DestinationType.CHIME, chimeDestinationFactory);

        String message = "{\"Content\":\"Message gughjhjlkh Body emoji test: :) :+1: link test: http://sample.com email test: marymajor@example.com All member callout: @All All Present member callout: @Present\"}";
        BaseMessage bm = new ChimeMessage.Builder("abc").withMessage(message).
                withUrl("https://abc/com").build();
        DestinationHttpResponse actualChimeResponse = (DestinationHttpResponse) Notification.publish(bm);

        assertEquals(expectedChimeResponse.getResponseString(), actualChimeResponse.getResponseString());
        assertEquals(expectedChimeResponse.getStatusCode(), actualChimeResponse.getStatusCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUrlMissingMessage() {
        try {
            ChimeMessage message = new ChimeMessage.Builder("chime").withMessage("dummyMessage").build();
        } catch (Exception ex) {
            assertEquals("url is invalid or empty", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentMissingMessage() {
        try {
            ChimeMessage message = new ChimeMessage.Builder("chime").withUrl("abc.com").build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }
}
