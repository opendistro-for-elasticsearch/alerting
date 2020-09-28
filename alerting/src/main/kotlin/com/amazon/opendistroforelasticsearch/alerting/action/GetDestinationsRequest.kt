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
    val destinationType: String

    constructor(
        destinationId: String?,
        version: Long,
        srcContext: FetchSourceContext?,
        table: Table,
        destinationType: String
    ) : super() {
        this.destinationId = destinationId
        this.version = version
        this.srcContext = srcContext
        this.table = table
        this.destinationType = destinationType
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readOptionalString(), // monitorId
        sin.readLong(), // version
        if (sin.readBoolean()) {
            FetchSourceContext(sin) // srcContext
        } else null,
        Table.readFrom(sin), // table
        sin.readString() // destinationType
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeOptionalString(destinationId)
        out.writeLong(version)
        out.writeBoolean(srcContext != null)
        srcContext?.writeTo(out)
        table.writeTo(out)
        out.writeString(destinationType)
    }
}
