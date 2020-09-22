package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import java.io.IOException

class GetAlertsRequest : ActionRequest {
    val sortOrder: String?
    val sortString: String?

    constructor(
        sortOrder: String,
        sortString: String
    ) : super() {
        this.sortOrder = sortOrder
        this.sortString = sortString
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
            sin.readString(), // sortOrder
            sin.readString() // sortString
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(sortOrder)
        out.writeString(sortString)
    }
}
