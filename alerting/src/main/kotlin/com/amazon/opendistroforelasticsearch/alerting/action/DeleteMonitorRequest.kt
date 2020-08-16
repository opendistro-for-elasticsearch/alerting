package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import java.io.IOException

class DeleteMonitorRequest : ActionRequest {

    val monitorId: String
    val refreshPolicy: WriteRequest.RefreshPolicy

    constructor(monitorId: String, refreshPolicy: WriteRequest.RefreshPolicy) : super() {
        this.monitorId = monitorId
        this.refreshPolicy = refreshPolicy
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : super() {
        monitorId = sin.readString()
        refreshPolicy = WriteRequest.RefreshPolicy.readFrom(sin)
    }

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(monitorId)
        refreshPolicy.writeTo(out)
    }
}
