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

package com.amazon.opendistro.notification.response;

import org.elasticsearch.common.Strings;

/**
 * This class is a place holder for SNS response metadata
 */
public class SNSResponse extends BaseResponse {
    private String messageId;

    private SNSResponse(final String messageId, final Integer statusCode) {
        super(statusCode);
        if (!Strings.hasLength(messageId)) {
            throw new IllegalArgumentException("MessageId is missing in the response");
        }
        this.messageId = messageId;
    }

    public static class Builder {
        private String messageId;
        private Integer statusCode;

        public Builder withMessageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder withStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public SNSResponse build() {
            return new SNSResponse(messageId, statusCode);
        }
    }

    public String getMessageId() {
        return this.messageId;
    }
}
