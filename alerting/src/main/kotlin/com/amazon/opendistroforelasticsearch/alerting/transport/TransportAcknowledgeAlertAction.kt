package com.amazon.opendistroforelasticsearch.alerting.transport

import com.amazon.opendistroforelasticsearch.alerting.action.AcknowledgeAlertAction
import com.amazon.opendistroforelasticsearch.alerting.action.AcknowledgeAlertRequest
import com.amazon.opendistroforelasticsearch.alerting.action.AcknowledgeAlertResponse
import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.optionalTimeField
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.util.AlertingException
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.Client
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService
import java.time.Instant

private val log = LogManager.getLogger(TransportAcknowledgeAlertAction::class.java)

class TransportAcknowledgeAlertAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<AcknowledgeAlertRequest, AcknowledgeAlertResponse>(
        AcknowledgeAlertAction.NAME, transportService, actionFilters, ::AcknowledgeAlertRequest
) {

    override fun doExecute(task: Task, request: AcknowledgeAlertRequest, actionListener: ActionListener<AcknowledgeAlertResponse>) {
        client.threadPool().threadContext.stashContext().use {
            AcknowledgeHandler(client, actionListener, request).start()
        }
    }

    inner class AcknowledgeHandler(
        private val client: Client,
        private val actionListener: ActionListener<AcknowledgeAlertResponse>,
        private val request: AcknowledgeAlertRequest
    ) {
        val alerts = mutableMapOf<String, Alert>()

        fun start() = findActiveAlerts()

        private fun findActiveAlerts() {
            val queryBuilder = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.termQuery(Alert.MONITOR_ID_FIELD, request.monitorId))
                    .filter(QueryBuilders.termsQuery("_id", request.alertIds))
            val searchRequest = SearchRequest()
                    .indices(AlertIndices.ALERT_INDEX)
                    .routing(request.monitorId)
                    .source(SearchSourceBuilder().query(queryBuilder).version(true).seqNoAndPrimaryTerm(true))

            client.search(searchRequest, object : ActionListener<SearchResponse> {
                override fun onResponse(response: SearchResponse) {
                    onSearchResponse(response)
                }

                override fun onFailure(t: Exception) {
                    actionListener.onFailure(AlertingException.wrap(t))
                }
            })
        }

        private fun onSearchResponse(response: SearchResponse) {
            val updateRequests = response.hits.flatMap { hit ->
                val xcp = XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                        hit.sourceRef, XContentType.JSON)
                XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
                val alert = Alert.parse(xcp, hit.id, hit.version)
                alerts[alert.id] = alert
                if (alert.state == Alert.State.ACTIVE) {
                    listOf(UpdateRequest(AlertIndices.ALERT_INDEX, hit.id)
                            .routing(request.monitorId)
                            .setIfSeqNo(hit.seqNo)
                            .setIfPrimaryTerm(hit.primaryTerm)
                            .doc(XContentFactory.jsonBuilder().startObject()
                                    .field(Alert.STATE_FIELD, Alert.State.ACKNOWLEDGED.toString())
                                    .optionalTimeField(Alert.ACKNOWLEDGED_TIME_FIELD, Instant.now())
                                    .endObject()))
                } else {
                    emptyList()
                }
            }

            log.info("Acknowledging monitor: $request.monitorId, alerts: ${updateRequests.map { it.id() }}")
            val bulkRequest = BulkRequest().add(updateRequests).setRefreshPolicy(request.refreshPolicy)
            client.bulk(bulkRequest, object : ActionListener<BulkResponse> {
                override fun onResponse(response: BulkResponse) {
                    onBulkResponse(response)
                }

                override fun onFailure(t: Exception) {
                    actionListener.onFailure(AlertingException.wrap(t))
                }
            })
        }

        private fun onBulkResponse(response: BulkResponse) {
            val missing = request.alertIds.toMutableSet()
            val acknowledged = mutableListOf<Alert>()
            val failed = mutableListOf<Alert>()
            // First handle all alerts that aren't currently ACTIVE. These can't be acknowledged.
            alerts.values.forEach {
                if (it.state != Alert.State.ACTIVE) {
                    missing.remove(it.id)
                    failed.add(it)
                }
            }
            // Now handle all alerts we tried to acknowledge...
            response.items.forEach { item ->
                missing.remove(item.id)
                if (item.isFailed) {
                    failed.add(alerts[item.id]!!)
                } else {
                    acknowledged.add(alerts[item.id]!!)
                }
            }
            actionListener.onResponse(AcknowledgeAlertResponse(acknowledged.toList(), failed.toList(), missing.toList()))
        }
    }
}
