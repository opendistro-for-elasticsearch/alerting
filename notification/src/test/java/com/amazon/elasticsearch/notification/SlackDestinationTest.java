package com.amazon.elasticsearch.notification;

import com.amazon.elasticsearch.notification.client.DestinationHttpClient;
import com.amazon.elasticsearch.notification.factory.DestinationFactoryProvider;
import com.amazon.elasticsearch.notification.factory.SlackDestinationFactory;
import com.amazon.elasticsearch.notification.message.BaseMessage;
import com.amazon.elasticsearch.notification.message.DestinationType;
import com.amazon.elasticsearch.notification.message.SlackMessage;
import com.amazon.elasticsearch.notification.response.DestinationHttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SlackDestinationTest {

    @Test
    public void testSlackMessage() throws Exception {

        CloseableHttpClient mockHttpClient = EasyMock.createMock(CloseableHttpClient.class);

        DestinationHttpResponse expectedSlackResponse = new DestinationHttpResponse.Builder().withResponseString("{}").withStatusCode(200).build();
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
        SlackDestinationFactory slackDestinationFactory = new SlackDestinationFactory();
        slackDestinationFactory.setClient(httpClient);

        DestinationFactoryProvider.setFactory(DestinationType.SLACK, slackDestinationFactory);

        String message = "{\"text\":\"Vamshi Message gughjhjlkh Body emoji test: :) :+1: link test: http://sample.com email test: marymajor@example.com All member callout: @All All Present member callout: @Present\"}";
        BaseMessage bm = new SlackMessage.Builder("abc").withMessage(message).
                withUrl("https://hooks.slack.com/services/xxxx/xxxxxx/xxxxxxxxx").build();

        DestinationHttpResponse actualSlackResponse = (DestinationHttpResponse) Notification.publish(bm);

        assertEquals(expectedSlackResponse.getResponseString(), actualSlackResponse.getResponseString());
        assertEquals(expectedSlackResponse.getStatusCode(), actualSlackResponse.getStatusCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUrlMissingMessage() {
        try {
            SlackMessage message = new SlackMessage.Builder("slack").withMessage("dummyMessage").build();
        } catch (Exception ex) {
            assertEquals("url is invalid or empty", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentMissingMessage() {
        try {
            SlackMessage message = new SlackMessage.Builder("slack").withUrl("abc.com").build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }
}