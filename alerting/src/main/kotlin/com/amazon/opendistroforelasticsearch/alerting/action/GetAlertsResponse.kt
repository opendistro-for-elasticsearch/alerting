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
