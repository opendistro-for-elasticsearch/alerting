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

/**
 * This class holds the content of an Slack message
 */
public class SlackMessage extends BaseMessage {
    private String message;
    private SlackMessage(final DestinationType destinationType,
                         final String destinationName,
                         final String url,
                         final String message) {

        super(destinationType, destinationName, message, url);

        if (DestinationType.SLACK != destinationType) {
            throw new IllegalArgumentException("Channel Type does not match Slack");
        }

        if (Strings.isNullOrEmpty(url)) { // add URL validation
            throw new IllegalArgumentException("Fully qualified URL is missing/invalid: " + url);
        }

        if (Strings.isNullOrEmpty(message)) {
            throw new IllegalArgumentException("Message content is missing");
        }

        this.message = message;
    }

    @Override
    public String toString() {
        return "DestinationType: " + destinationType + ", DestinationName:" +  destinationName +
                ", Url: " + url + ", Message: " + message;
    }

    public static class Builder {
        private String message;
        private DestinationType destinationType;
        private String destinationName;
        private String url;

        public Builder(String channelName) {
            this.destinationName = channelName;
            this.destinationType = DestinationType.SLACK;
        }

        public SlackMessage.Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public SlackMessage.Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public SlackMessage build() {
            SlackMessage slackMessage = new SlackMessage(this.destinationType,
                    this.destinationName,
                    this.url,
                    this.message);
            return slackMessage;
        }
    }

    public String getMessage() {
        return message;
    }

    public String getUrl() {
        return url;
    }
}
