/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import java.io.IOException

class SearchMonitorRequest : ActionRequest {

    val searchRequest: SearchRequest
    val authHeader: String?

    constructor(
        searchRequest: SearchRequest,
        authHeader: String?
    ): super() {
        this.searchRequest = searchRequest
        this.authHeader = authHeader
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
            searchRequest = SearchRequest(sin),
            authHeader = sin.readOptionalString()
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        searchRequest.writeTo(out)
        out.writeOptionalString(authHeader)
    }
}
