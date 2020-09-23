package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import java.io.IOException
import java.util.Collections

class GetAlertsRequest : ActionRequest {
    val table: Table
    val severityLevel: String
    val alertState: String
    val monitorIds: List<String>

    constructor(
        table: Table,
        severityLevel: String,
        alertState: String,
        monitorIds: List<String>
    ) : super() {
        this.table = table
        this.severityLevel = severityLevel
        this.alertState = alertState
        this.monitorIds = monitorIds
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        Table.readFrom(sin), // table
        sin.readString(), // severityLevel
        sin.readString(), // alertState
        Collections.unmodifiableList(sin.readStringList()) // monitorIds
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        table.writeTo(out)
    }
}
