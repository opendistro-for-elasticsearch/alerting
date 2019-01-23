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

import com.amazon.opendistro.notification.client.DestinationHttpClient;
import com.amazon.opendistro.notification.factory.CustomWebhookDestinationFactory;
import com.amazon.opendistro.notification.factory.DestinationFactoryProvider;
import com.amazon.opendistro.notification.message.BaseMessage;
import com.amazon.opendistro.notification.message.CustomWebhookMessage;
import com.amazon.opendistro.notification.message.DestinationType;
import com.amazon.opendistro.notification.response.DestinationHttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CustomWebhookMessageTest {

    @Test
    public void testCustomWebhookMessage() throws Exception {

        CloseableHttpClient mockHttpClient = EasyMock.createMock(CloseableHttpClient.class);

        DestinationHttpResponse expectedCustomWebhookResponse = new DestinationHttpResponse.Builder().withResponseString("{}").withStatusCode(200).build();
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
        CustomWebhookDestinationFactory customDestinationFactory = new CustomWebhookDestinationFactory();
        customDestinationFactory.setClient(httpClient);

        DestinationFactoryProvider.setFactory(DestinationType.CUSTOMWEBHOOK, customDestinationFactory);

        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("token", "R2x1UlN4ZHF8MXxxVFJpelJNVDgzdGNwMnVRenJwRFBHUkR0NlhROWhXOVVTZXpiTWx2azVr");

        String message = "{\"Content\":\"Message gughjhjlkh Body emoji test: :) :+1: link test: http://sample.com email test: marymajor@example.com All member callout: @All All Present member callout: @Present\"}";
        BaseMessage bm = new CustomWebhookMessage.Builder("abc").withHost("hooks.chime.aws").
                withPath("incomingwebhooks/383c0e2b-d028-44f4-8d38-696754bc4574").
                withMessage(message).
                withQueryParams(queryParams).build();
        DestinationHttpResponse actualCustomResponse = (DestinationHttpResponse) Notification.publish(bm);

        assertEquals(expectedCustomWebhookResponse.getResponseString(), actualCustomResponse.getResponseString());
        assertEquals(expectedCustomWebhookResponse.getStatusCode(), actualCustomResponse.getStatusCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUrlMissingMessage() {
        try {
            CustomWebhookMessage message = new CustomWebhookMessage.Builder("custom").withMessage("dummyMessage").build();
        } catch (Exception ex) {
            assertEquals("Either fully qualified URL or host name should be provided", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentMissingMessage() {
        try {
            CustomWebhookMessage message = new CustomWebhookMessage.Builder("custom").withUrl("abc.com").build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }
}
