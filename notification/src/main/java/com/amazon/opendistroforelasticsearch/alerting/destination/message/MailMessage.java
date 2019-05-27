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

package com.amazon.opendistroforelasticsearch.alerting.destination.message;

import org.elasticsearch.common.Strings;

import java.util.Map;

/**
 * This class holds the content of a Mail message
 */
public class MailMessage extends BaseMessage {

    private String message;
    private String host;
    private int port;
    private Boolean auth;
    private String method;
    private String from;
    private String recipients;
    private String subject;
    private final String userName;
    private final String password;

    private MailMessage(final DestinationType destinationType,
                                 final String destinationName,
                                 final String host,
                                 final Integer port,
                                 final Boolean auth,
                                 final String method,
                                 final String from,
                                 final String recipients,
                                 final String subject,
                                 final String userName,
                                 final String password,
                                 final String message) {

        super(destinationType, destinationName, message);

        if (DestinationType.MAIL != destinationType) {
            throw new IllegalArgumentException("Channel Type does not match Mail");
        }

        if (Strings.isNullOrEmpty(message)) {
            throw new IllegalArgumentException("Message content is missing");
        }

        if(Strings.isNullOrEmpty(host)) {
            throw new IllegalArgumentException("Host name should be provided");
        }

        if(Strings.isNullOrEmpty(from)) {
            throw new IllegalArgumentException("From address should be provided");
        }

        if(Strings.isNullOrEmpty(recipients)) {
            throw new IllegalArgumentException("Comma separated recipients should be provided");
        }

        this.message = message;
        this.host = host;
        this.port = port==null ? 25 : port;
        this.method = method==null ? "plain" : method;
        this.auth = auth==null ? false : auth;
        this.from = from;
        this.recipients = recipients;
        this.subject = subject == "" ? destinationName : subject;
        this.userName = userName;
        this.password = password;
    }

    @Override
    public String toString() {
        return "DestinationType: " + destinationType + ", DestinationName:" +  destinationName +
                ", Host: " + host + ", Port: " + port + ", Message: " + message;
    }

    public static class Builder {
        private String message;
        private DestinationType destinationType;
        private String destinationName;
        private String host;
        private Integer port;
        private Boolean auth;
        private String method;
        private String from;
        private String recipients;
        private String subject;
        private String userName;
        private String password;

        public Builder(String destinationName) {
            this.destinationName = destinationName;
            this.destinationType = DestinationType.MAIL;
        }

        public MailMessage.Builder withHost(String host) {
            this.host = host;
            return this;
        }

        public MailMessage.Builder withPort(Integer port) {
            this.port = port;
            return this;
        }

        public MailMessage.Builder withAuth(Boolean auth) {
            this.auth = auth;
            return this;
        }

        public MailMessage.Builder withMethod(String method) {
            this.method = method;
            return this;
        }

        public MailMessage.Builder withFrom(String from) {
            this.from = from;
            return this;
        }

        public MailMessage.Builder withRecipients(String recipients) {
            this.recipients = recipients;
            return this;
        }

        public MailMessage.Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public MailMessage.Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public MailMessage.Builder withUserName(String userName) {
            this.userName = userName;
            return this;
        }

        public MailMessage.Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public MailMessage build() {
            MailMessage mailMessage = new MailMessage(
                    this.destinationType, this.destinationName,
                    this.host, this.port, this.auth, this.method,
                    this.from, this.recipients, this.subject,
                    this.userName, this.password, this.message);
            return mailMessage;
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Boolean getAuthEnable() {
        return auth;
    }

    public String getMethod() {
        return method;
    }
    
    public String getFrom() {
        return from;
    }

    public String getRecipients() {
        return recipients;
    }

    public String getSubject() {
        return subject;
    }

    public String getUsername() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
}