package com.amazon.opendistroforelasticsearch.alerting.transport

import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsAction
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsRequest
import com.amazon.opendistroforelasticsearch.alerting.action.GetAlertsResponse
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

private val log = LogManager.getLogger(TransportGetAlertsAction::class.java)

class TransportGetAlertsAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<GetAlertsRequest, GetAlertsResponse>(
        GetAlertsAction.NAME, transportService, actionFilters, ::GetAlertsRequest
) {

    override fun doExecute(
        task: Task,
        getDestinationsRequest: GetAlertsRequest,
        actionListener: ActionListener<GetAlertsResponse>
    ) {
        client.threadPool().threadContext.stashContext().use {
            val searchRequest = SearchRequest()
                    .indices(AlertIndices.ALL_INDEX_PATTERN)
                    .source(SearchSourceBuilder().version(true).seqNoAndPrimaryTerm(true)
                            .sort(getDestinationsRequest.sortString, SortOrder.fromString(getDestinationsRequest.sortOrder)))

            client.search(searchRequest, object : ActionListener<SearchResponse> {
                override fun onResponse(response: SearchResponse) {
                    val alerts = response.hits.map { hit ->
                        val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                                hit.sourceRef, XContentType.JSON)
                        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                        val alert = Alert.parse(xcp, hit.id, hit.version)
                        alert
                    }
                    actionListener.onResponse(GetAlertsResponse(alerts))
                }

                override fun onFailure(t: Exception) {
                    log.error("fail to get alerts", t)
                    actionListener.onFailure(t)
                }
            })
        }
    }
}
