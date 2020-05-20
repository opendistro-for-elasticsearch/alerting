/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.INDEX_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.alerting.util._ID
import com.amazon.opendistroforelasticsearch.alerting.util._VERSION
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.util.IF_PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util.IF_SEQ_NO
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import com.amazon.opendistroforelasticsearch.alerting.util._PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util._SEQ_NO
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.index.seqno.SequenceNumbers
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException

private val log = LogManager.getLogger(RestIndexDestinationAction::class.java)

/**
 * Rest handlers to create and update Destination
 */
class RestIndexDestinationAction(
    settings: Settings,
    jobIndices: ScheduledJobIndices,
    clusterService: ClusterService
) : BaseRestHandler() {
    private var scheduledJobIndices: ScheduledJobIndices
    private val clusterService: ClusterService
    @Volatile private var indexTimeout = INDEX_TIMEOUT.get(settings)

    init {
        scheduledJobIndices = jobIndices

        clusterService.clusterSettings.addSettingsUpdateConsumer(INDEX_TIMEOUT) { indexTimeout = it }
        this.clusterService = clusterService
    }

    override fun getName(): String {
        return "index_destination_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.POST, AlertingPlugin.DESTINATION_BASE_URI), // Creates new destination
                Route(RestRequest.Method.PUT, "${AlertingPlugin.DESTINATION_BASE_URI}/{destinationID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer {
        val id = request.param("destinationID", Destination.NO_ID)
        if (request.method() == RestRequest.Method.PUT && Destination.NO_ID == id) {
            throw IllegalArgumentException("Missing destination ID")
        }

        // Validate request by parsing JSON to Destination
        val xcp = request.contentParser()
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        val destination = Destination.parse(xcp, id)
        val seqNo = request.paramAsLong(IF_SEQ_NO, SequenceNumbers.UNASSIGNED_SEQ_NO)
        val primaryTerm = request.paramAsLong(IF_PRIMARY_TERM, SequenceNumbers.UNASSIGNED_PRIMARY_TERM)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }
        return RestChannelConsumer { channel ->
            IndexDestinationHandler(client, channel, id, seqNo, primaryTerm, refreshPolicy, destination).start()
        }
    }

    inner class IndexDestinationHandler(
        client: NodeClient,
        channel: RestChannel,
        private val destinationId: String,
        private val seqNo: Long,
        private val primaryTerm: Long,
        private val refreshPolicy: WriteRequest.RefreshPolicy,
        private var newDestination: Destination
    ) : AsyncActionHandler(client, channel) {

        fun start() {
            if (!scheduledJobIndices.scheduledJobIndexExists()) {
                scheduledJobIndices.initScheduledJobIndex(ActionListener.wrap(::onCreateMappingsResponse, ::onFailure))
            } else {
                if (!IndexUtils.scheduledJobIndexUpdated) {
                    IndexUtils.updateIndexMapping(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE,
                            ScheduledJobIndices.scheduledJobMappings(), clusterService.state(), client.admin().indices(),
                            ActionListener.wrap(::onUpdateMappingsResponse, ::onFailure))
                } else {
                    prepareDestinationIndexing()
                }
            }
        }

        private fun prepareDestinationIndexing() {
            if (channel.request().method() == RestRequest.Method.PUT) updateDestination()
            else {
                newDestination = newDestination.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
                val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                        .setRefreshPolicy(refreshPolicy)
                        .source(newDestination.toXContent(channel.newBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                        .setIfSeqNo(seqNo)
                        .setIfPrimaryTerm(primaryTerm)
                        .timeout(indexTimeout)
                client.index(indexRequest, indexDestinationResponse())
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                log.info("Created ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
                prepareDestinationIndexing()
                IndexUtils.scheduledJobIndexUpdated()
            } else {
                log.error("Create ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                channel.sendResponse(BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR,
                        response.toXContent(channel.newErrorBuilder(), ToXContent.EMPTY_PARAMS)))
            }
        }

        private fun onUpdateMappingsResponse(response: AcknowledgedResponse) {
            if (response.isAcknowledged) {
                log.info("Updated  ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
                IndexUtils.scheduledJobIndexUpdated()
                prepareDestinationIndexing()
            } else {
                log.error("Update ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                channel.sendResponse(BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR,
                        response.toXContent(channel.newErrorBuilder().startObject()
                                .field("message", "Updated ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                                .endObject(), ToXContent.EMPTY_PARAMS)))
            }
        }

        private fun updateDestination() {
            val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, destinationId)
            client.get(getRequest, ActionListener.wrap(::onGetResponse, ::onFailure))
        }

        private fun onGetResponse(response: GetResponse) {
            if (!response.isExists) {
                val builder = channel.newErrorBuilder()
                        .startObject()
                        .field("Message", "Destination with $destinationId is not found")
                        .endObject()
                return channel.sendResponse(BytesRestResponse(RestStatus.NOT_FOUND, response.toXContent(builder, ToXContent.EMPTY_PARAMS)))
            }
            newDestination = newDestination.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
            val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                    .setRefreshPolicy(refreshPolicy)
                    .source(newDestination.toXContent(channel.newBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                    .id(destinationId)
                    .setIfSeqNo(seqNo)
                    .setIfPrimaryTerm(primaryTerm)
                    .timeout(indexTimeout)
            return client.index(indexRequest, indexDestinationResponse())
        }

        private fun indexDestinationResponse(): RestResponseListener<IndexResponse> {
            return object : RestResponseListener<IndexResponse>(channel) {
                @Throws(Exception::class)
                override fun buildResponse(response: IndexResponse): RestResponse {
                    val failureReasons = mutableListOf<String>()
                    if (response.shardInfo.failed > 0) {
                        response.shardInfo.failures.forEach { entry -> failureReasons.add(entry.reason()) }
                        val builder = channel.newErrorBuilder().startObject()
                                .field("reasons", failureReasons.toTypedArray())
                                .endObject()
                        return BytesRestResponse(response.status(), response.toXContent(builder, ToXContent.EMPTY_PARAMS))
                    }
                    val builder = channel.newBuilder()
                            .startObject()
                            .field(_ID, response.id)
                            .field(_VERSION, response.version)
                            .field(_SEQ_NO, response.seqNo)
                            .field(_PRIMARY_TERM, response.primaryTerm)
                            .field("destination", newDestination)
                            .endObject()

                    val restResponse = BytesRestResponse(response.status(), builder)
                    if (response.status() == RestStatus.CREATED) {
                        val location = AlertingPlugin.DESTINATION_BASE_URI + response.id
                        restResponse.addHeader("Location", location)
                    }
                    return restResponse
                }
            }
        }
    }
}
