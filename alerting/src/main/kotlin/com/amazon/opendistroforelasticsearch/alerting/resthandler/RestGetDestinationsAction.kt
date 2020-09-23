package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.action.GetDestinationsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetDestinationsRequest
import com.amazon.opendistroforelasticsearch.alerting.model.Table
import com.amazon.opendistroforelasticsearch.alerting.util.context
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.RestHandler
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestToXContentListener
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import org.apache.logging.log4j.LogManager

/**
 * This class consists of the REST handler to retrieve destinations .
 */
class RestGetDestinationsAction : BaseRestHandler() {

    private val log = LogManager.getLogger(RestGetDestinationsAction::class.java)

    override fun getName(): String {
        return "get_destinations_action"
    }

    override fun routes(): List<RestHandler.Route> {
        return listOf(
                // Get a specific destination
                RestHandler.Route(RestRequest.Method.GET, "${AlertingPlugin.DESTINATION_BASE_URI}/{destinationID}"),
                RestHandler.Route(RestRequest.Method.GET, "${AlertingPlugin.DESTINATION_BASE_URI}/all")
        )
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val destinationId: String? = request.param("destinationID")

        var srcContext = context(request)
        if (request.method() == RestRequest.Method.HEAD) {
            srcContext = FetchSourceContext.DO_NOT_FETCH_SOURCE
        }

        val sortString = request.param("sortString", "destination.name.keyword")
        val sortOrder = request.param("sortOrder", "asc")
        val missing: String? = request.param("missing")
        val size = request.paramAsInt("size", 20)
        val startIndex = request.paramAsInt("startIndex", 0)
        val searchString = request.param("searchString", "")
        val destinationType = request.param("destinationType", "ALL")

        val table = Table(
                sortOrder,
                sortString,
                missing,
                size,
                startIndex,
                searchString
        )

        val getDestinationsRequest = GetDestinationsRequest(
                destinationId,
                RestActions.parseVersion(request),
                srcContext,
                table,
                destinationType
        )
        return RestChannelConsumer {
            channel -> client.execute(GetDestinationsAction.INSTANCE, getDestinationsRequest, RestToXContentListener(channel))
        }
    }
}
