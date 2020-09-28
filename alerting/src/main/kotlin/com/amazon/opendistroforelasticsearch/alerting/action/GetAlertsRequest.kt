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

import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import java.io.IOException

class GetAlertsRequest : ActionRequest {
    val table: Table
    val severityLevel: String
    val alertState: String
    val monitorId: String?

    constructor(
        table: Table,
        severityLevel: String,
        alertState: String,
        monitorId: String?
    ) : super() {
        this.table = table
        this.severityLevel = severityLevel
        this.alertState = alertState
        this.monitorId = monitorId
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        table = Table.readFrom(sin),
        severityLevel = sin.readString(),
        alertState = sin.readString(),
        monitorId = sin.readOptionalString()
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        table.writeTo(out)
        out.writeString(severityLevel)
        out.writeString(alertState)
        out.writeOptionalString(monitorId)
    }
}
