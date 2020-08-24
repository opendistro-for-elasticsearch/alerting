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
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.unit.TimeValue
import java.io.IOException

class ExecuteMonitorRequest : ActionRequest {
    val dryrun: Boolean
    val requestEnd: TimeValue
    val monitorId: String?
    val monitor: Monitor?

    constructor(
        dryrun: Boolean,
        requestEnd: TimeValue,
        monitorId: String?,
        monitor: Monitor?
    ) : super() {
        this.dryrun = dryrun
        this.requestEnd = requestEnd
        this.monitorId = monitorId
        this.monitor = monitor
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readBoolean(), // dryrun
        sin.readTimeValue(), // requestEnd
        sin.readOptionalString(), // monitorId
        if (sin.readBoolean()) {
            Monitor.readFrom(sin) // monitor
        } else null
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeBoolean(dryrun)
        out.writeTimeValue(requestEnd)
        out.writeOptionalString(monitorId)
        if (monitor != null) {
            out.writeBoolean(true)
            monitor.writeTo(out)
        } else {
            out.writeBoolean(false)
        }
    }
}
