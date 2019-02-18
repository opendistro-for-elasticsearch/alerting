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
 * This class holds the contents of an Chime message
 */
public class ChimeMessage extends BaseMessage {
    private String message;
    private ChimeMessage(final DestinationType destinationType,
                         final String destinationName,
                         final String url,
                         final String message) {

        super(destinationType, destinationName, message, url);

        if (DestinationType.CHIME != destinationType) {
            throw new IllegalArgumentException("Channel Type does not match CHIME");
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

        public Builder(String destinationName) {
            this.destinationName = destinationName;
            this.destinationType = DestinationType.CHIME;
        }

        public ChimeMessage.Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public ChimeMessage.Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public ChimeMessage build() {
            ChimeMessage chimeMessage = new ChimeMessage(this.destinationType, this.destinationName, this.url,
                     this.message);
            return chimeMessage;
        }
    }

    public String getUrl() {
        return url;
    }
}
