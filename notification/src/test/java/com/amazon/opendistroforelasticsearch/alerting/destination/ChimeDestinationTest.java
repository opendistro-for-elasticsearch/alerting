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

import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationHttpClient;
import com.amazon.opendistroforelasticsearch.alerting.destination.factory.ChimeDestinationFactory;
import com.amazon.opendistroforelasticsearch.alerting.destination.factory.DestinationFactoryProvider;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.ChimeMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.DestinationType;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.easymock.EasyMock;
import org.elasticsearch.rest.RestStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChimeDestinationTest {

    @Test
    public void testChimeMessage_NullEntityResponse() throws Exception {
        CloseableHttpClient mockHttpClient = EasyMock.createMock(CloseableHttpClient.class);

        // The DestinationHttpClient replaces a null entity with "{}".
        DestinationResponse expectedChimeResponse = new DestinationResponse.Builder()
                .withResponseContent("{}")
                .withStatusCode(RestStatus.OK.getStatus())
                .build();
        CloseableHttpResponse httpResponse = EasyMock.createMock(CloseableHttpResponse.class);
        EasyMock.expect(mockHttpClient.execute(EasyMock.anyObject(HttpPost.class))).andReturn(httpResponse);

        BasicStatusLine mockStatusLine = EasyMock.createMock(BasicStatusLine.class);

        EasyMock.expect(httpResponse.getStatusLine()).andReturn(mockStatusLine);
        EasyMock.expect(httpResponse.getEntity()).andReturn(null).anyTimes();
        EasyMock.expect(mockStatusLine.getStatusCode()).andReturn(RestStatus.OK.getStatus());
        EasyMock.replay(mockHttpClient);
        EasyMock.replay(httpResponse);
        EasyMock.replay(mockStatusLine);

        DestinationHttpClient httpClient = new DestinationHttpClient();
        httpClient.setHttpClient(mockHttpClient);
        ChimeDestinationFactory chimeDestinationFactory = new ChimeDestinationFactory();
        chimeDestinationFactory.setClient(httpClient);

        DestinationFactoryProvider.setFactory(DestinationType.CHIME, chimeDestinationFactory);

        String message = "{\"Content\":\"Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new ChimeMessage.Builder("abc").withMessage(message).
                withUrl("https://abc/com").build();
        DestinationResponse actualChimeResponse = (DestinationResponse) Notification.publish(bm);

        assertEquals(expectedChimeResponse.getResponseContent(), actualChimeResponse.getResponseContent());
        assertEquals(expectedChimeResponse.getStatusCode(), actualChimeResponse.getStatusCode());
    }

    @Test
    public void testChimeMessage_EmptyEntityResponse() throws Exception {
        CloseableHttpClient mockHttpClient = EasyMock.createMock(CloseableHttpClient.class);

        DestinationResponse expectedChimeResponse = new DestinationResponse.Builder()
                .withResponseContent("")
                .withStatusCode(RestStatus.OK.getStatus())
                .build();
        CloseableHttpResponse httpResponse = EasyMock.createMock(CloseableHttpResponse.class);
        EasyMock.expect(mockHttpClient.execute(EasyMock.anyObject(HttpPost.class))).andReturn(httpResponse);

        BasicStatusLine mockStatusLine = EasyMock.createMock(BasicStatusLine.class);

        EasyMock.expect(httpResponse.getStatusLine()).andReturn(mockStatusLine);
        EasyMock.expect(httpResponse.getEntity()).andReturn(new StringEntity("")).anyTimes();
        EasyMock.expect(mockStatusLine.getStatusCode()).andReturn(RestStatus.OK.getStatus());
        EasyMock.replay(mockHttpClient);
        EasyMock.replay(httpResponse);
        EasyMock.replay(mockStatusLine);

        DestinationHttpClient httpClient = new DestinationHttpClient();
        httpClient.setHttpClient(mockHttpClient);
        ChimeDestinationFactory chimeDestinationFactory = new ChimeDestinationFactory();
        chimeDestinationFactory.setClient(httpClient);

        DestinationFactoryProvider.setFactory(DestinationType.CHIME, chimeDestinationFactory);

        String message = "{\"Content\":\"Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new ChimeMessage.Builder("abc").withMessage(message).
                withUrl("https://abc/com").build();
        DestinationResponse actualChimeResponse = (DestinationResponse) Notification.publish(bm);

        assertEquals(expectedChimeResponse.getResponseContent(), actualChimeResponse.getResponseContent());
        assertEquals(expectedChimeResponse.getStatusCode(), actualChimeResponse.getStatusCode());
    }

    @Test
    public void testChimeMessage_NonemptyEntityResponse() throws Exception {
        String responseContent = "It worked!";

        CloseableHttpClient mockHttpClient = EasyMock.createMock(CloseableHttpClient.class);

        DestinationResponse expectedChimeResponse = new DestinationResponse.Builder().withResponseContent(responseContent)
                .withStatusCode(RestStatus.OK.getStatus()).build();
        CloseableHttpResponse httpResponse = EasyMock.createMock(CloseableHttpResponse.class);
        EasyMock.expect(mockHttpClient.execute(EasyMock.anyObject(HttpPost.class))).andReturn(httpResponse);

        BasicStatusLine mockStatusLine = EasyMock.createMock(BasicStatusLine.class);

        EasyMock.expect(httpResponse.getStatusLine()).andReturn(mockStatusLine);
        EasyMock.expect(httpResponse.getEntity()).andReturn(new StringEntity(responseContent)).anyTimes();
        EasyMock.expect(mockStatusLine.getStatusCode()).andReturn(RestStatus.OK.getStatus());
        EasyMock.replay(mockHttpClient);
        EasyMock.replay(httpResponse);
        EasyMock.replay(mockStatusLine);

        DestinationHttpClient httpClient = new DestinationHttpClient();
        httpClient.setHttpClient(mockHttpClient);
        ChimeDestinationFactory chimeDestinationFactory = new ChimeDestinationFactory();
        chimeDestinationFactory.setClient(httpClient);

        DestinationFactoryProvider.setFactory(DestinationType.CHIME, chimeDestinationFactory);

        String message = "{\"Content\":\"Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new ChimeMessage.Builder("abc").withMessage(message).
                withUrl("https://abc/com").build();
        DestinationResponse actualChimeResponse = (DestinationResponse) Notification.publish(bm);

        assertEquals(expectedChimeResponse.getResponseContent(), actualChimeResponse.getResponseContent());
        assertEquals(expectedChimeResponse.getStatusCode(), actualChimeResponse.getStatusCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUrlMissingMessage() {
        try {
            ChimeMessage message = new ChimeMessage.Builder("chime")
                    .withMessage("dummyMessage").build();
        } catch (Exception ex) {
            assertEquals("url is invalid or empty", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentMissingMessage() {
        try {
            ChimeMessage message = new ChimeMessage.Builder("chime")
                    .withUrl("abc.com").build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }
}
