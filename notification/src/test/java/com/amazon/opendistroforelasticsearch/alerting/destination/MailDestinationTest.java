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
import com.amazon.opendistroforelasticsearch.alerting.destination.factory.MailDestinationFactory;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.DestinationType;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.MailMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.client.DestinationMailClient;
import com.amazon.opendistroforelasticsearch.alerting.destination.response.DestinationMailResponse;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import javax.mail.MessagingException;

import static org.junit.Assert.assertEquals;

public class MailDestinationTest {

    @Test
    public void testMailMessage() throws Exception {

        DestinationMailResponse expectedMailResponse = new DestinationMailResponse.Builder().withResponseContent("Sent")
                .withStatusCode(0).build();

        DestinationMailClient mailClient = EasyMock.partialMockBuilder(DestinationMailClient.class).addMockedMethod("SendMessage").createMock();
        mailClient.SendMessage(EasyMock.anyObject(Message.class));

        MailDestinationFactory mailDestinationFactory = new MailDestinationFactory();
        mailDestinationFactory.setClient(mailClient);

        DestinationFactoryProvider.setFactory(DestinationType.MAIL, mailDestinationFactory);

        String message = "{\"text\":\"Vamshi Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new MailMessage.Builder("abc")
                .withMessage(message)
                .withHost("abc.com")
                .withFrom("test@abc.com")
                .withRecipients("test@abc.com").build();

        DestinationMailResponse actualMailResponse = (DestinationMailResponse) Notification.publish(bm);
        assertEquals(expectedMailResponse.getResponseContent(), actualMailResponse.getResponseContent());
        assertEquals(expectedMailResponse.getStatusCode(), actualMailResponse.getStatusCode());
    }

    @Test
    public void testFailingMailMessage() throws Exception {

        DestinationMailResponse expectedMailResponse = new DestinationMailResponse.Builder().withResponseContent("Couldn't connect to host, port: localhost, 55555; timeout -1")
                .withStatusCode(1).build();

        DestinationMailClient mailClient = EasyMock.partialMockBuilder(DestinationMailClient.class).addMockedMethod("SendMessage").createMock();
        mailClient.SendMessage(EasyMock.anyObject(Message.class));
        EasyMock.expectLastCall().andThrow(new MessagingException("Couldn't connect to host, port: localhost, 55555; timeout -1"));
        EasyMock.replay(mailClient);

        MailDestinationFactory mailDestinationFactory = new MailDestinationFactory();
        mailDestinationFactory.setClient(mailClient);

        DestinationFactoryProvider.setFactory(DestinationType.MAIL, mailDestinationFactory);

        String message = "{\"text\":\"Vamshi Message gughjhjlkh Body emoji test: :) :+1: " +
                "link test: http://sample.com email test: marymajor@example.com All member callout: " +
                "@All All Present member callout: @Present\"}";
        BaseMessage bm = new MailMessage.Builder("abc")
                .withMessage(message)
                .withHost("localhost")
                .withPort(55555)
                .withFrom("test@abc.com")
                .withRecipients("test@abc.com").build();

        DestinationMailResponse actualMailResponse = (DestinationMailResponse) Notification.publish(bm);
        EasyMock.verify(mailClient);
        assertEquals(expectedMailResponse.getResponseContent(), actualMailResponse.getResponseContent());
        assertEquals(expectedMailResponse.getStatusCode(), actualMailResponse.getStatusCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHostMissingMessage() {
        try {
            MailMessage message = new MailMessage.Builder("mail")
                    .withMessage("dummyMessage")
                    .withFrom("test@abc.com")
                    .withRecipients("test@abc.com").build();
                    
        } catch (Exception ex) {
            Assert.assertEquals("Host name should be provided", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentMissingMessage() {
        try {
            MailMessage message = new MailMessage.Builder("mail")
                    .withHost("abc.com")
                    .withFrom("test@abc.com")
                    .withRecipients("test@abc.com").build();
        } catch (Exception ex) {
            assertEquals("Message content is missing", ex.getMessage());
            throw ex;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromMissingMessage() {
        try {
            MailMessage message = new MailMessage.Builder("mail")
                    .withMessage("dummyMessage")
                    .withHost("abc.com")
                    .withRecipients("test@abc.com").build();
                    
        } catch (Exception ex) {
            Assert.assertEquals("From address should be provided", ex.getMessage());
            throw ex;
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRecipientsMissingMessage() {
        try {
            MailMessage message = new MailMessage.Builder("mail")
                    .withMessage("dummyMessage")
                    .withHost("abc.com")
                    .withFrom("test@abc.com").build();
                    
        } catch (Exception ex) {
            Assert.assertEquals("Comma separated recipients should be provided", ex.getMessage());
            throw ex;
        }
    }
}
