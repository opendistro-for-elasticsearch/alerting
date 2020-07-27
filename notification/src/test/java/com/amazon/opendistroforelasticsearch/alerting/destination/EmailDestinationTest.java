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

import com.amazon.opendistroforelasticsearch.alerting.destination.factory.DestinationFactoryProvider;
import com.amazon.opendistroforelasticsearch.alerting.destination.factory.EmailDestinationFactory;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.DestinationType;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.EmailMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationEmailClient;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationEmailResponse;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import javax.mail.Message;
import javax.mail.MessagingException;

import java.util.Arrays;
import java.util.Collections;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class EmailDestinationTest {

    @Test
    public void testMailMessage() throws Exception {

        DestinationEmailResponse expectedEmailResponse = new DestinationEmailResponse.Builder()
                .withResponseContent("Sent")
                .withStatusCode(0).build();

        DestinationEmailClient emailClient = EasyMock.partialMockBuilder(DestinationEmailClient.class)
                .addMockedMethod("SendMessage").createMock();
        emailClient.SendMessage(EasyMock.anyObject(Message.class));

        EmailDestinationFactory emailDestinationFactory = new EmailDestinationFactory();
        emailDestinationFactory.setClient(emailClient);

        DestinationFactoryProvider.setFactory(DestinationType.EMAIL, emailDestinationFactory);

        String message = "{\"text\":\"Vamshi Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new EmailMessage.Builder("abc")
                .withMessage(message)
                .withHost("abc.com")
                .withFrom("test@abc.com")
                .withRecipients(singletonList("test@abc.com")).build();

        DestinationEmailResponse actualEmailResponse = (DestinationEmailResponse) Notification.publish(bm);
        assertEquals(expectedEmailResponse.getResponseContent(), actualEmailResponse.getResponseContent());
        assertEquals(expectedEmailResponse.getStatusCode(), actualEmailResponse.getStatusCode());
    }

    @Test
    public void testFailingMailMessage() throws Exception {

        DestinationEmailResponse expectedEmailResponse = new DestinationEmailResponse.Builder()
                .withResponseContent("Couldn't connect to host, port: localhost, 55555; timeout -1")
                .withStatusCode(1).build();

        DestinationEmailClient emailClient = EasyMock.partialMockBuilder(DestinationEmailClient.class)
                .addMockedMethod("SendMessage").createMock();
        emailClient.SendMessage(EasyMock.anyObject(Message.class));
        EasyMock.expectLastCall()
                .andThrow(new MessagingException("Couldn't connect to host, port: localhost, 55555; timeout -1"));
        EasyMock.replay(emailClient);

        EmailDestinationFactory emailDestinationFactory = new EmailDestinationFactory();
        emailDestinationFactory.setClient(emailClient);

        DestinationFactoryProvider.setFactory(DestinationType.EMAIL, emailDestinationFactory);

        String message = "{\"text\":\"Vamshi Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new EmailMessage.Builder("abc")
                .withMessage(message)
                .withHost("localhost")
                .withPort(55555)
                .withFrom("test@abc.com")
                .withRecipients(singletonList("test@abc.com")).build();

        DestinationEmailResponse actualEmailResponse = (DestinationEmailResponse) Notification.publish(bm);
        EasyMock.verify(emailClient);
        assertEquals(expectedEmailResponse.getResponseContent(), actualEmailResponse.getResponseContent());
        assertEquals(expectedEmailResponse.getStatusCode(), actualEmailResponse.getStatusCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHostMissingMessage() {
        try {
            EmailMessage message = new EmailMessage.Builder("mail")
                    .withMessage("dummyMessage")
                    .withFrom("test@abc.com")
                    .withRecipients(singletonList("test@abc.com")).build();

        } catch (Exception ex) {
            Assert.assertEquals("Host name should be provided", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentMissingMessage() {
        try {
            EmailMessage message = new EmailMessage.Builder("mail")
                    .withHost("abc.com")
                    .withFrom("test@abc.com")
                    .withRecipients(singletonList("test@abc.com")).build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromMissingMessage() {
        try {
            EmailMessage message = new EmailMessage.Builder("mail")
                    .withMessage("dummyMessage")
                    .withHost("abc.com")
                    .withRecipients(singletonList("test@abc.com")).build();

        } catch (Exception ex) {
            Assert.assertEquals("From address should be provided", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecipientsMissingMessage() {
        try {
            EmailMessage message = new EmailMessage.Builder("mail")
                    .withMessage("dummyMessage")
                    .withHost("abc.com")
                    .withFrom("test@abc.com").build();

        } catch (Exception ex) {
            Assert.assertEquals("List of recipients should be provided", ex.getMessage());
            throw ex;
        }
    }
}
