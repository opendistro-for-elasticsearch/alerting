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
import com.amazon.opendistroforelasticsearch.alerting.util._ID
import com.amazon.opendistroforelasticsearch.alerting.util._PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util._SEQ_NO
import com.amazon.opendistroforelasticsearch.alerting.util._VERSION
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.rest.RestStatus
import java.io.IOException

class GetMonitorResponse : ActionResponse, ToXContentObject {
    var id: String
    var version: Long
    var seqNo: Long
    var primaryTerm: Long
    var status: RestStatus
    var monitor: Monitor?

    constructor(
        id: String,
        version: Long,
        seqNo: Long,
        primaryTerm: Long,
        status: RestStatus,
        monitor: Monitor?
    ) : super() {
        this.id = id
        this.version = version
        this.seqNo = seqNo
        this.primaryTerm = primaryTerm
        this.status = status
        this.monitor = monitor
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readString(), // id
        sin.readLong(), // version
        sin.readLong(), // seqNo
        sin.readLong(), // primaryTerm
        sin.readEnum(RestStatus::class.java), // RestStatus
        if (sin.readBoolean()) {
            Monitor.readFrom(sin) // monitor
        } else null
    )

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeLong(version)
        out.writeLong(seqNo)
        out.writeLong(primaryTerm)
        out.writeEnum(status)
        if (monitor != null) {
            out.writeBoolean(true)
            monitor?.writeTo(out)
        } else {
            out.writeBoolean(false)
        }
    }

    @Throws(IOException::class)
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .field(_ID, id)
                .field(_VERSION, version)
                .field(_SEQ_NO, seqNo)
                .field(_PRIMARY_TERM, primaryTerm)
        if (monitor != null)
            builder.field("monitor", monitor)

        return builder.endObject()
    }
}
