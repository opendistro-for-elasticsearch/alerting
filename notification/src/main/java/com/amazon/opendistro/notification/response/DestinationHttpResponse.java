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
 * This class is a place holder for http response metadata
 */
public class DestinationHttpResponse extends BaseResponse {

    private String responseString;

    private DestinationHttpResponse(final String responseString, final int statusCode) {
        super(statusCode);
        if (Strings.isNullOrEmpty(responseString)) {
            throw new IllegalArgumentException("Response is missing");
        }
        this.responseString = responseString;
    }

    public static class Builder {
        private String responseString;
        private Integer statusCode = null;

        public DestinationHttpResponse.Builder withResponseString(String responseString) {
            this.responseString = responseString;
            return this;
        }

        public DestinationHttpResponse.Builder withStatusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public DestinationHttpResponse build() {
            return new DestinationHttpResponse(responseString, statusCode);
        }
    }

    public String getResponseString() {
        return this.responseString;
    }
}
