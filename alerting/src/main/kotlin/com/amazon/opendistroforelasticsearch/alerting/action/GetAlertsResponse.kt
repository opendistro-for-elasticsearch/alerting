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

import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import java.io.IOException
import java.util.Collections

class GetAlertsResponse : ActionResponse, ToXContentObject {
    val alerts: List<Alert>
    // totalAlerts is not the same as the size of alerts because there can be 30 alerts from the request, but
    // the request only asked for 5 alerts, so totalAlerts will be 30, but alerts will only contain 5 alerts
    val totalAlerts: Int?

    constructor(
        alerts: List<Alert>,
        totalAlerts: Int?
    ) : super() {
        this.alerts = alerts
        this.totalAlerts = totalAlerts
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        alerts = Collections.unmodifiableList(sin.readList(::Alert)),
        totalAlerts = sin.readOptionalInt()
    )

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeCollection(alerts)
        out.writeOptionalInt(totalAlerts)
    }

    @Throws(IOException::class)
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .field("alerts", alerts)
                .field("totalAlerts", totalAlerts)

        return builder.endObject()
    }
}
