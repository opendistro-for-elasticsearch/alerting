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
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BaseRestHandler.RestChannelConsumer
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestActions
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException

/**
 * Rest handlers to create and update Destination
 */
class RestIndexDestinationAction(
    settings: Settings,
    controller: RestController,
    jobIndices: ScheduledJobIndices,
    clusterService: ClusterService
) : BaseRestHandler(settings) {
    private var scheduledJobIndices: ScheduledJobIndices
    @Volatile private var indexTimeout = INDEX_TIMEOUT.get(settings)

    init {
        controller.registerHandler(RestRequest.Method.POST, AlertingPlugin.DESTINATION_BASE_URI, this) // Creates new destination
        controller.registerHandler(RestRequest.Method.PUT, "${AlertingPlugin.DESTINATION_BASE_URI}/{destinationID}", this)
        scheduledJobIndices = jobIndices

        clusterService.clusterSettings.addSettingsUpdateConsumer(INDEX_TIMEOUT) { indexTimeout = it }
    }

    override fun getName(): String {
        return "index_destination_action"
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
        val destintaionVersion = RestActions.parseVersion(request)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }
        return RestChannelConsumer { channel ->
            IndexDestinationHandler(client, channel, id, destintaionVersion, refreshPolicy, destination).start()
        }
    }

    inner class IndexDestinationHandler(
        client: NodeClient,
        channel: RestChannel,
        private val destinationId: String,
        private val destinationVersion: Long,
        private val refreshPolicy: WriteRequest.RefreshPolicy,
        private var newDestination: Destination
    ) : AsyncActionHandler(client, channel) {

        fun start() {
            if (!scheduledJobIndices.scheduledJobIndexExists()) {
                scheduledJobIndices.initScheduledJobIndex(ActionListener.wrap(::onCreateMappingsResponse, ::onFailure))
            } else {
                prepareDestinationIndexing()
            }
        }

        private fun prepareDestinationIndexing() {
            if (channel.request().method() == RestRequest.Method.PUT) updateDestination()
            else {
                val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
                        .setRefreshPolicy(refreshPolicy)
                        .source(newDestination.toXContent(channel.newBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                        .version(destinationVersion)
                        .timeout(indexTimeout)
                client.index(indexRequest, indexDestinationResponse())
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                logger.info("Created ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
                prepareDestinationIndexing()
            } else {
                logger.error("Create ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                channel.sendResponse(BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR,
                        response.toXContent(channel.newErrorBuilder(), ToXContent.EMPTY_PARAMS)))
            }
        }

        private fun updateDestination() {
            val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE, destinationId)
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
            val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
                    .setRefreshPolicy(refreshPolicy)
                    .source(newDestination.toXContent(channel.newBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                    .id(destinationId)
                    .version(destinationVersion)
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
