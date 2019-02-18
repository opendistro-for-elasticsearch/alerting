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
import com.amazon.opendistroforelasticsearch.alerting.destination.factory.DestinationFactoryProvider;
import com.amazon.opendistroforelasticsearch.alerting.destination.factory.SlackDestinationFactory;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.DestinationType;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.SlackMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationHttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.easymock.EasyMock;
import org.elasticsearch.rest.RestStatus;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SlackDestinationTest {

    @Test
    public void testSlackMessage() throws Exception {

        CloseableHttpClient mockHttpClient = EasyMock.createMock(CloseableHttpClient.class);

        DestinationHttpResponse expectedSlackResponse = new DestinationHttpResponse.Builder().withResponseContent("{}")
                .withStatusCode(RestStatus.OK.getStatus()).build();
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
        SlackDestinationFactory slackDestinationFactory = new SlackDestinationFactory();
        slackDestinationFactory.setClient(httpClient);

        DestinationFactoryProvider.setFactory(DestinationType.SLACK, slackDestinationFactory);

        String message = "{\"text\":\"Vamshi Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new SlackMessage.Builder("abc").withMessage(message).
                withUrl("https://hooks.slack.com/services/xxxx/xxxxxx/xxxxxxxxx").build();

        DestinationHttpResponse actualSlackResponse = (DestinationHttpResponse) Notification.publish(bm);

        assertEquals(expectedSlackResponse.getResponseContent(), actualSlackResponse.getResponseContent());
        assertEquals(expectedSlackResponse.getStatusCode(), actualSlackResponse.getStatusCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUrlMissingMessage() {
        try {
            SlackMessage message = new SlackMessage.Builder("slack")
                    .withMessage("dummyMessage").build();
        } catch (Exception ex) {
            Assert.assertEquals("url is invalid or empty", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentMissingMessage() {
        try {
            SlackMessage message = new SlackMessage.Builder("slack")
                    .withUrl("abc.com").build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }
}