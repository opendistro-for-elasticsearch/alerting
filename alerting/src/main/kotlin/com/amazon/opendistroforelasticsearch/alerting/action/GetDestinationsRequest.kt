package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import java.io.IOException

class GetDestinationsRequest : ActionRequest {
    val destinationId: String?
    val version: Long
    val sortOrder: String?
    val sortString: String?
    val srcContext: FetchSourceContext?

    constructor(
        destinationId: String?,
        version: Long,
        sortOrder: String?,
        sortString: String?,
        srcContext: FetchSourceContext?
    ) : super() {
        this.destinationId = destinationId
        this.version = version
        this.sortOrder = sortOrder
        this.sortString = sortString
        this.srcContext = srcContext
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readString(), // monitorId
        sin.readLong(), // version
        sin.readString(), // sortOrder
        sin.readString(), // sortString
        FetchSourceContext(sin) // srcContext
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(destinationId)
        out.writeLong(version)
        out.writeString(sortOrder)
        out.writeString(sortString)
        srcContext?.writeTo(out)
    }
}
