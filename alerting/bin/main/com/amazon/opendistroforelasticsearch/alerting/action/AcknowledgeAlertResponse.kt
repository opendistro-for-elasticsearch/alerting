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

class AcknowledgeAlertResponse : ActionResponse, ToXContentObject {

    val acknowledged: List<Alert>
    val failed: List<Alert>
    val missing: List<String>

    constructor(
        acknowledged: List<Alert>,
        failed: List<Alert>,
        missing: List<String>
    ) : super() {
        this.acknowledged = acknowledged
        this.failed = failed
        this.missing = missing
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        Collections.unmodifiableList(sin.readList(::Alert)), // acknowledged
        Collections.unmodifiableList(sin.readList(::Alert)), // failed
        Collections.unmodifiableList(sin.readStringList()) // missing
    )

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeCollection(acknowledged)
        out.writeCollection(failed)
        out.writeStringCollection(missing)
    }

    @Throws(IOException::class)
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {

        builder.startObject().startArray("success")
        acknowledged.forEach { builder.value(it.id) }
        builder.endArray().startArray("failed")
        failed.forEach { buildFailedAlertAcknowledgeObject(builder, it) }
        missing.forEach { buildMissingAlertAcknowledgeObject(builder, it) }
        return builder.endArray().endObject()
    }

    private fun buildFailedAlertAcknowledgeObject(builder: XContentBuilder, failedAlert: Alert) {
        builder.startObject()
                .startObject(failedAlert.id)
        val reason = when (failedAlert.state) {
            Alert.State.ERROR -> "Alert is in an error state and can not be acknowledged."
            Alert.State.COMPLETED -> "Alert has already completed and can not be acknowledged."
            Alert.State.ACKNOWLEDGED -> "Alert has already been acknowledged."
            else -> "Alert state unknown and can not be acknowledged"
        }
        builder.field("failed_reason", reason)
                .endObject()
                .endObject()
    }

    private fun buildMissingAlertAcknowledgeObject(builder: XContentBuilder, alertID: String) {
        builder.startObject()
                .startObject(alertID)
                .field("failed_reason", "Alert: $alertID does not exist (it may have already completed).")
                .endObject()
                .endObject()
    }
}
