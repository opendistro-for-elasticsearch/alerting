package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import java.io.IOException

class GetDestinationsRequest : ActionRequest {
    val destinationId: String?
    val version: Long
    val srcContext: FetchSourceContext?
    val table: Table

    constructor(
        destinationId: String?,
        version: Long,
        srcContext: FetchSourceContext?,
        table: Table
    ) : super() {
        this.destinationId = destinationId
        this.version = version
        this.srcContext = srcContext
        this.table = table
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readOptionalString(), // monitorId
        sin.readLong(), // version
        FetchSourceContext(sin), // srcContext
        Table.readFrom(sin)
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeOptionalString(destinationId)
        out.writeLong(version)
        srcContext?.writeTo(out)
        table.writeTo(out)
    }
}
