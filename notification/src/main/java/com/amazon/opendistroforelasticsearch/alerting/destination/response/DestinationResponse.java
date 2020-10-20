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

package com.amazon.opendistroforelasticsearch.alerting.destination.response;

/**
 * This class is a place holder for destination response metadata
 */
public class DestinationResponse extends BaseResponse {

    private String responseContent;

    private DestinationResponse(final String responseString, final int statusCode) {
        super(statusCode);
        if (responseString == null) {
            throw new IllegalArgumentException("Response is missing");
        }
        this.responseContent = responseString;
    }

    public static class Builder {
        private String responseContent;
        private Integer statusCode = null;

        public DestinationResponse.Builder withResponseContent(String responseContent) {
            this.responseContent = responseContent;
            return this;
        }

        public DestinationResponse.Builder withStatusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public DestinationResponse build() {
            return new DestinationResponse(responseContent, statusCode);
        }
    }

    public String getResponseContent() {
        return this.responseContent;
    }
}
