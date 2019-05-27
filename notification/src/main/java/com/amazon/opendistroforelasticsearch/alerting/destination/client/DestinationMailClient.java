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

package com.amazon.opendistroforelasticsearch.alerting.destination.client;

import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage;
import com.amazon.opendistroforelasticsearch.alerting.destination.message.MailMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Strings;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * This class handles the connections to the given Destination.
 */
public class DestinationMailClient {

    private static final Logger logger = LogManager.getLogger(DestinationMailClient.class);
    
    public String execute(BaseMessage message) throws Exception {
        if (message instanceof MailMessage) {
            MailMessage mailMessage = (MailMessage) message;
            Session session = null;

            Properties prop = new Properties();
            prop.put("mail.transport.protocol", "smtp");
            prop.put("mail.smtp.host", mailMessage.getHost());
            prop.put("mail.smtp.port", mailMessage.getPort());

            prop.put("mail.smtp.auth", mailMessage.getAuthEnable());
            if ( mailMessage.getAuthEnable() ) {
                session = Session.getInstance(prop, new javax.mail.Authenticator() {
	    	        protected PasswordAuthentication getPasswordAuthentication() {
		    	        return new PasswordAuthentication(mailMessage.getUsername(), mailMessage.getPassword());
		            }
	            });
            } else {
                session = Session.getInstance(prop);
            }

            switch(mailMessage.getMethod()) {
            case "ssl":
                prop.put("mail.smtp.ssl.enable", true);
                break;
            case "starttls":
                prop.put("mail.smtp.starttls.enable", true);
                break;
            }

            try {
                Message mailmsg = new MimeMessage(session);
                mailmsg.setFrom(new InternetAddress(mailMessage.getFrom()));
                mailmsg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mailMessage.getRecipients()));
                mailmsg.setSubject(mailMessage.getSubject());

                MimeBodyPart mimeBodyPart = new MimeBodyPart();
                mimeBodyPart.setContent(message.getMessageContent(), "text/html");
                Multipart multipart = new MimeMultipart();
                multipart.addBodyPart(mimeBodyPart);

                mailmsg.setContent(multipart);
                SendMessage(mailmsg);
            } catch (MessagingException e) {
                return e.getMessage();
            }
        }
        return "Sent";
    }

    /*
     * This method is useful for Mocking the client
     */
    public void SendMessage(Message msg) throws Exception {
        Transport.send(msg);
    }

}
