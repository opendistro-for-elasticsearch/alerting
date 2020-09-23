package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsRequest
import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.apache.logging.log4j.LogManager
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method.GET
import org.elasticsearch.rest.action.RestToXContentListener

/**
 * This class consists of the REST handler to retrieve alerts .
 */
class RestGetAlertsAction : BaseRestHandler() {

    private val log = LogManager.getLogger(RestGetAlertsAction::class.java)

    override fun getName(): String {
        return "get_alerts_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(GET, "${AlertingPlugin.MONITOR_BASE_URI}/alerts")
        )
    }

    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val sortString = request.param("sortString", "name")
        val sortOrder = request.param("sortOrder", "asc")
        val missing: String? = request.param("missing")
        val size = request.paramAsInt("size", 20)
        val startIndex = request.paramAsInt("startIndex", 0)

        val table = Table(
                sortOrder,
                sortString,
                missing,
                size,
                startIndex
        )
        val getAlertsRequest = GetAlertsRequest(table)
        return RestChannelConsumer {
            channel -> client.execute(GetAlertsAction.INSTANCE, getAlertsRequest, RestToXContentListener(channel))
        }
    }
}
