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

import com.amazon.opendistroforelasticsearch.alerting.destination.util.Util;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.SecureString;


/**
 * This class holds the content of an SNS message
 */
public class SNSMessage extends BaseMessage {

    private String subject;
    private String message;
    private String roleArn;
    private String topicArn;
    private SecureString iamAccessKey;
    public SecureString iamSecretKey;

    private SNSMessage(final DestinationType destinationType, final String destinationName, final String roleArn,
                       final String topicArn, final SecureString iamAccessKey, final SecureString iamSecretKey,
                       final String subject, final String message) {

        super(destinationType, destinationName, message);

        if (DestinationType.SNS != destinationType) {
            throw new IllegalArgumentException("Channel Type does not match SNS");
        }

        if (roleArn == null && (iamAccessKey == null || Strings.isNullOrEmpty(iamAccessKey.toString()))) {
            throw new IllegalArgumentException("IAM user access key is missing");
        }

        if (roleArn == null && (iamSecretKey == null || Strings.isNullOrEmpty(iamSecretKey.toString()))) {
            throw new IllegalArgumentException("IAM user secret key is missing");
        }

        if (Strings.isNullOrEmpty(topicArn) || !Util.isValidSNSArn(topicArn)) {
            throw new IllegalArgumentException("Topic arn is missing/invalid: " + topicArn);
        }

        if (roleArn != null && !Util.isValidIAMArn(roleArn)) {
            throw new IllegalArgumentException("Role arn is invalid: " + roleArn);
        }

        if (Strings.isNullOrEmpty(message)) {
            throw new IllegalArgumentException("Message content is missing");
        }

        this.subject = subject;
        this.message = message;
        this.roleArn = roleArn;
        this.topicArn = topicArn;
        this.iamAccessKey = iamAccessKey;
        this.iamSecretKey = iamSecretKey;
    }

    @Override
    public String toString() {

        String credentialKey = ", roleArn: " + roleArn;
        if (roleArn == null) {
            credentialKey = ", AccessKey: " + iamAccessKey;
        }

        return "DestinationType: " + destinationType + ", DestinationName:" +  destinationName +
                credentialKey + ", TopicArn: " + topicArn + ", Subject: " + subject +
                ", Message: " + message;
    }

    public static class Builder {
        private String subject;
        private String message;
        private String roleArn;
        private String topicArn;
        private SecureString iamAccessKey;
        private SecureString iamSecretKey;
        private DestinationType destinationType;
        private String destinationName;

        public Builder(String destinationName) {
            this.destinationName = destinationName;
            this.destinationType = DestinationType.SNS;
        }

        public Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder withRoleArn(String roleArn) {
            this.roleArn = roleArn;
            return this;
        }

        public Builder withTopicArn(String topicArn) {
            this.topicArn = topicArn;
            return this;
        }

        public Builder withIAMAccessKey(SecureString iamAccessKey) {
            this.iamAccessKey = iamAccessKey;
            return this;
        }

        public Builder withIAMSecretKey(SecureString iamSecretKey) {
            this.iamSecretKey = iamSecretKey;
            return this;
        }

        public SNSMessage build() {
            SNSMessage snsMessage = new SNSMessage(this.destinationType, this.destinationName, this.roleArn, this.topicArn,
                    this.iamAccessKey, this.iamSecretKey, this.subject, this.message);

            return snsMessage;
        }
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getTopicArn() {
        return topicArn;
    }

    public SecureString getIAMAccessKey() {
        return iamAccessKey;
    }

    public SecureString getIAMSecretKey() {
        return iamSecretKey;
    }

}
