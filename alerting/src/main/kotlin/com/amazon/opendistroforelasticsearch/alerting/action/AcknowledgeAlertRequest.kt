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
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import java.io.IOException
import java.util.Collections

class AcknowledgeAlertRequest : ActionRequest {
    val monitorId: String
    val alertIds: List<String>
    val refreshPolicy: WriteRequest.RefreshPolicy

    constructor(
        monitorId: String,
        alertIds: List<String>,
        refreshPolicy: WriteRequest.RefreshPolicy
    ) : super() {
        this.monitorId = monitorId
        this.alertIds = alertIds
        this.refreshPolicy = refreshPolicy
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readString(), // monitorId
        Collections.unmodifiableList(sin.readStringList()), // alertIds
        WriteRequest.RefreshPolicy.readFrom(sin) // refreshPolicy
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(monitorId)
        out.writeStringCollection(alertIds)
        refreshPolicy.writeTo(out)
    }
}
