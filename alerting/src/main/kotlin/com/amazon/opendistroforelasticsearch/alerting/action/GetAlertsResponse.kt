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

    constructor(
        alerts: List<Alert>
    ) : super() {
        this.alerts = alerts
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
            Collections.unmodifiableList(sin.readList(::Alert)) // alerts
    )

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeCollection(alerts)
    }

    @Throws(IOException::class)
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {

        builder.startObject()
                .field("alerts", alerts)

        return builder.endObject()
    }
}
