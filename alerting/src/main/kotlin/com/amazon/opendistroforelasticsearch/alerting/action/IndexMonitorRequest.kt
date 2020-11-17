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

import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.rest.RestRequest
import java.io.IOException

class IndexMonitorRequest : ActionRequest {
    val monitorId: String
    val seqNo: Long
    val primaryTerm: Long
    val refreshPolicy: WriteRequest.RefreshPolicy
    val method: RestRequest.Method
    val authHeader: String?
    var monitor: Monitor

    constructor(
        monitorId: String,
        seqNo: Long,
        primaryTerm: Long,
        refreshPolicy: WriteRequest.RefreshPolicy,
        method: RestRequest.Method,
        authHeader: String?,
        monitor: Monitor
    ): super() {
        this.monitorId = monitorId
        this.seqNo = seqNo
        this.primaryTerm = primaryTerm
        this.refreshPolicy = refreshPolicy
        this.method = method
        this.authHeader = authHeader
        this.monitor = monitor
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        monitorId = sin.readString(),
        seqNo = sin.readLong(),
        primaryTerm = sin.readLong(),
        refreshPolicy = WriteRequest.RefreshPolicy.readFrom(sin),
        method = sin.readEnum(RestRequest.Method::class.java),
        authHeader = sin.readOptionalString(),
        monitor = Monitor.readFrom(sin) as Monitor
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(monitorId)
        out.writeLong(seqNo)
        out.writeLong(primaryTerm)
        refreshPolicy.writeTo(out)
        out.writeEnum(method)
        out.writeOptionalString(authHeader)
        monitor.writeTo(out)
    }
}
