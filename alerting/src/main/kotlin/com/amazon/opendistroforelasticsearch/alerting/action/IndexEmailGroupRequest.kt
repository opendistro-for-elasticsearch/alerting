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

import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.rest.RestRequest
import java.io.IOException

class IndexEmailGroupRequest : ActionRequest {
    var emailGroupID: String
    var seqNo: Long
    var primaryTerm: Long
    var refreshPolicy: WriteRequest.RefreshPolicy
    var method: RestRequest.Method
    var emailGroup: EmailGroup

    constructor(
        emailGroupID: String,
        seqNo: Long,
        primaryTerm: Long,
        refreshPolicy: WriteRequest.RefreshPolicy,
        method: RestRequest.Method,
        emailGroup: EmailGroup
    ): super() {
        this.emailGroupID = emailGroupID
        this.seqNo = seqNo
        this.primaryTerm = primaryTerm
        this.refreshPolicy = refreshPolicy
        this.method = method
        this.emailGroup = emailGroup
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readString(), // emailGroupID
        sin.readLong(), // seqNo
        sin.readLong(), // primaryTerm
        WriteRequest.RefreshPolicy.readFrom(sin), // refreshPolicy
        sin.readEnum(RestRequest.Method::class.java), // method
        EmailGroup.readFrom(sin) // emailGroup
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(emailGroupID)
        out.writeLong(seqNo)
        out.writeLong(primaryTerm)
        refreshPolicy.writeTo(out)
        out.writeEnum(method)
        emailGroup.writeTo(out)
    }
}
