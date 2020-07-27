package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.util.IF_PRIMARY_TERM
import com.amazon.opendistroforelasticsearch.alerting.util.IF_SEQ_NO
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import com.amazon.opendistroforelasticsearch.alerting.util._ID
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
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException

private val log = LogManager.getLogger(RestIndexEmailGroupAction::class.java)

/**
 * Rest handlers to create and update EmailGroup.
 */
class RestIndexEmailGroupAction(
    settings: Settings,
    jobIndices: ScheduledJobIndices,
    clusterService: ClusterService
) : BaseRestHandler() {

    private var scheduledJobIndices: ScheduledJobIndices
    private var clusterService: ClusterService
    @Volatile private var indexTimeout = AlertingSettings.INDEX_TIMEOUT.get(settings)

    init {
        scheduledJobIndices = jobIndices

        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.INDEX_TIMEOUT) { indexTimeout = it }
        this.clusterService = clusterService
    }

    override fun getName(): String {
        return "index_email_group_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.POST, AlertingPlugin.EMAIL_GROUP_BASE_URI), // Creates new email group
                Route(RestRequest.Method.PUT, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/{emailGroupID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val id = request.param("emailGroupID", EmailGroup.NO_ID)
        if (request.method() == RestRequest.Method.PUT && EmailGroup.NO_ID == id) {
            throw IllegalArgumentException("Missing email group ID")
        }

        // Validate request by parsing JSON to EmailGroup
        val xcp = request.contentParser()
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
        val emailGroup = EmailGroup.parse(xcp, id)
        val seqNo = request.paramAsLong(IF_SEQ_NO, SequenceNumbers.UNASSIGNED_SEQ_NO)
        val primaryTerm = request.paramAsLong(IF_PRIMARY_TERM, SequenceNumbers.UNASSIGNED_PRIMARY_TERM)
        val refreshPolicy = if (request.hasParam(REFRESH)) {
            WriteRequest.RefreshPolicy.parse(request.param(REFRESH))
        } else {
            WriteRequest.RefreshPolicy.IMMEDIATE
        }

        return RestChannelConsumer { channel ->
            IndexEmailGroupHandler(client, channel, id, seqNo, primaryTerm, refreshPolicy, emailGroup).start()
        }
    }

    inner class IndexEmailGroupHandler(
        client: NodeClient,
        channel: RestChannel,
        private val emailGroupID: String,
        private val seqNo: Long,
        private val primaryTerm: Long,
        private val refreshPolicy: WriteRequest.RefreshPolicy,
        private var newEmailGroup: EmailGroup
    ) : AsyncActionHandler(client, channel) {

        fun start() {
            if (!scheduledJobIndices.scheduledJobIndexExists()) {
                scheduledJobIndices.initScheduledJobIndex(ActionListener.wrap(::onCreateMappingsResponse, ::onFailure))
            } else {
                if (!IndexUtils.scheduledJobIndexUpdated) {
                    IndexUtils.updateIndexMapping(SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE,
                            ScheduledJobIndices.scheduledJobMappings(), clusterService.state(), client.admin().indices(),
                            ActionListener.wrap(::onUpdateMappingsResponse, ::onFailure))
                } else {
                    prepareEmailGroupIndexing()
                }
            }
        }

        private fun prepareEmailGroupIndexing() {
            if (channel.request().method() == RestRequest.Method.PUT) updateEmailGroup()
            else {
                newEmailGroup = newEmailGroup.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
                val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                        .setRefreshPolicy(refreshPolicy)
                        .source(newEmailGroup.toXContent(channel.newBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                        .setIfSeqNo(seqNo)
                        .setIfPrimaryTerm(primaryTerm)
                        .timeout(indexTimeout)
                client.index(indexRequest, indexEmailGroupResponse())
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                log.info("Created ${SCHEDULED_JOBS_INDEX} with mappings.")
                prepareEmailGroupIndexing()
                IndexUtils.scheduledJobIndexUpdated()
            } else {
                log.error("Create ${SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                channel.sendResponse(BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR,
                        response.toXContent(channel.newErrorBuilder(), ToXContent.EMPTY_PARAMS)))
            }
        }

        private fun onUpdateMappingsResponse(response: AcknowledgedResponse) {
            if (response.isAcknowledged) {
                log.info("Updated ${SCHEDULED_JOBS_INDEX} with mappings.")
                IndexUtils.scheduledJobIndexUpdated()
                prepareEmailGroupIndexing()
            } else {
                log.error("Update ${SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                channel.sendResponse(BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR,
                        response.toXContent(channel.newErrorBuilder().startObject()
                                .field("message", "Updated ${SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                                .endObject(), ToXContent.EMPTY_PARAMS)))
            }
        }

        private fun updateEmailGroup() {
            val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, emailGroupID)
            client.get(getRequest, ActionListener.wrap(::onGetResponse, ::onFailure))
        }

        private fun onGetResponse(response: GetResponse) {
            if (!response.isExists) {
                val builder = channel.newErrorBuilder()
                        .startObject()
                        .field("Message", "EmailGroup with $emailGroupID was not found")
                        .endObject()
                return channel.sendResponse(BytesRestResponse(RestStatus.NOT_FOUND, response.toXContent(builder, ToXContent.EMPTY_PARAMS)))
            }

            newEmailGroup = newEmailGroup.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
            val indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                    .setRefreshPolicy(refreshPolicy)
                    .source(newEmailGroup.toXContent(channel.newBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                    .id(emailGroupID)
                    .setIfSeqNo(seqNo)
                    .setIfPrimaryTerm(primaryTerm)
                    .timeout(indexTimeout)
            return client.index(indexRequest, indexEmailGroupResponse())
        }

        private fun indexEmailGroupResponse(): RestResponseListener<IndexResponse> {
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
                            .field(_SEQ_NO, response.seqNo)
                            .field(_PRIMARY_TERM, response.primaryTerm)
                            .field("email_group", newEmailGroup)
                            .endObject()

                    val restResponse = BytesRestResponse(response.status(), builder)
                    if (response.status() == RestStatus.CREATED) {
                        val location = AlertingPlugin.EMAIL_GROUP_BASE_URI + response.id
                        restResponse.addHeader("Location", location)
                    }

                    return restResponse
                }
            }
        }
    }
}
