package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.rest.RestStatus
import java.io.IOException

class GetDestinationsResponse : ActionResponse, ToXContentObject {
    var status: RestStatus
    var totalDestinations: Int
    var destinations: List<Destination>

    constructor(
        status: RestStatus,
        totalDestinations: Int,
        destinations: List<Destination>
    ) : super() {
        this.status = status
        this.totalDestinations = totalDestinations
        this.destinations = destinations
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) {
        this.status = sin.readEnum(RestStatus::class.java)
        val destinations = mutableListOf<Destination>()
        this.totalDestinations = sin.readInt()
        var currentSize = sin.readInt()
        for (i in 0 until currentSize) {
            destinations.add(Destination.readFrom(sin))
        }
        this.destinations = destinations
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeEnum(status)
        out.writeInt(totalDestinations)
        out.writeInt(destinations.size)
        for (destination in destinations) {
            destination.writeTo(out)
        }
    }

    @Throws(IOException::class)
    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .field("totalDestinations", totalDestinations)
                .field("destinations", destinations)

        return builder.endObject()
    }
}
