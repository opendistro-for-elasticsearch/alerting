package com.amazon.opendistroforelasticsearch.alerting.transport

import com.amazon.opendistroforelasticsearch.alerting.action.IndexDestinationAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexDestinationRequest
import com.amazon.opendistroforelasticsearch.alerting.action.IndexDestinationResponse
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings
import com.amazon.opendistroforelasticsearch.alerting.util.AlertingException
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import com.amazon.opendistroforelasticsearch.alerting.util.checkFilterByUserBackendRoles
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService
import java.io.IOException

private val log = LogManager.getLogger(TransportIndexDestinationAction::class.java)

class TransportIndexDestinationAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val scheduledJobIndices: ScheduledJobIndices,
    val clusterService: ClusterService,
    settings: Settings
) : HandledTransportAction<IndexDestinationRequest, IndexDestinationResponse>(
        IndexDestinationAction.NAME, transportService, actionFilters, ::IndexDestinationRequest
) {

    @Volatile private var indexTimeout = AlertingSettings.INDEX_TIMEOUT.get(settings)
    @Volatile private var allowList = DestinationSettings.ALLOW_LIST.get(settings)
    @Volatile private var filterByEnabled = AlertingSettings.FILTER_BY_BACKEND_ROLES.get(settings)
    private var user: User? = null

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.INDEX_TIMEOUT) { indexTimeout = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(DestinationSettings.ALLOW_LIST) { allowList = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.FILTER_BY_BACKEND_ROLES) { filterByEnabled = it }
    }

    override fun doExecute(task: Task, request: IndexDestinationRequest, actionListener: ActionListener<IndexDestinationResponse>) {
        val userStr = client.threadPool().threadContext.getTransient<String>(ConfigConstants.OPENDISTRO_SECURITY_USER_AND_ROLES)
        log.debug("User and roles string from thread context: $userStr")
        user = User.parse(userStr)

        if (!checkFilterByUserBackendRoles(filterByEnabled, user, actionListener)) {
            return
        }
        client.threadPool().threadContext.stashContext().use {
            IndexDestinationHandler(client, actionListener, request, user).resolveUserAndStart()
        }
    }

    inner class IndexDestinationHandler(
        private val client: Client,
        private val actionListener: ActionListener<IndexDestinationResponse>,
        private val request: IndexDestinationRequest,
        private val user: User?
    ) {

        fun resolveUserAndStart() {
            if (user == null) {
                // Security is disabled, add empty user to destination. user is null for older versions.
                request.destination = request.destination
                        .copy(user = User("", listOf(), listOf(), listOf()))
                start()
            } else {
                try {
                    request.destination = request.destination
                            .copy(user = User(user.name, user.backendRoles, user.roles, user.customAttNames))
                    start()
                } catch (ex: IOException) {
                    actionListener.onFailure(AlertingException.wrap(ex))
                }
            }
        }

        fun start() {
            if (!scheduledJobIndices.scheduledJobIndexExists()) {
                scheduledJobIndices.initScheduledJobIndex(object : ActionListener<CreateIndexResponse> {
                    override fun onResponse(response: CreateIndexResponse) {
                        onCreateMappingsResponse(response)
                    }
                    override fun onFailure(t: Exception) {
                        actionListener.onFailure(AlertingException.wrap(t))
                    }
                })
            } else if (!IndexUtils.scheduledJobIndexUpdated) {
                    IndexUtils.updateIndexMapping(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE,
                            ScheduledJobIndices.scheduledJobMappings(), clusterService.state(), client.admin().indices(),
                            object : ActionListener<AcknowledgedResponse> {
                                override fun onResponse(response: AcknowledgedResponse) {
                                    onUpdateMappingsResponse(response)
                                }
                                override fun onFailure(t: Exception) {
                                    actionListener.onFailure(AlertingException.wrap(t))
                                }
                            })
            } else {
                prepareDestinationIndexing()
            }
        }

        private fun prepareDestinationIndexing() {

            // Prevent indexing if the Destination type is disallowed
            val destinationType = request.destination.type.value
            if (!allowList.contains(destinationType)) {
                actionListener.onFailure(
                    AlertingException.wrap(ElasticsearchStatusException(
                        "Destination type is not allowed: $destinationType",
                        RestStatus.FORBIDDEN
                    ))
                )
                return
            }

            if (request.method == RestRequest.Method.PUT) updateDestination()
            else {
                val destination = request.destination.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
                val indexRequest = IndexRequest(ScheduledJob.SCHEDULED_JOBS_INDEX)
                        .setRefreshPolicy(request.refreshPolicy)
                        .source(destination.toXContent(jsonBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                        .setIfSeqNo(request.seqNo)
                        .setIfPrimaryTerm(request.primaryTerm)
                        .timeout(indexTimeout)

                client.index(indexRequest, object : ActionListener<IndexResponse> {
                    override fun onResponse(response: IndexResponse) {
                        val failureReasons = checkShardsFailure(response)
                        if (failureReasons != null) {
                            actionListener.onFailure(
                                    AlertingException.wrap(ElasticsearchStatusException(failureReasons.toString(), response.status())))
                            return
                        }
                        actionListener.onResponse(IndexDestinationResponse(response.id, response.version, response.seqNo,
                                response.primaryTerm, RestStatus.CREATED, destination))
                    }
                    override fun onFailure(t: Exception) {
                        actionListener.onFailure(AlertingException.wrap(t))
                    }
                })
            }
        }
        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                log.info("Created ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
                prepareDestinationIndexing()
                IndexUtils.scheduledJobIndexUpdated()
            } else {
                log.error("Create ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                actionListener.onFailure(AlertingException.wrap(
                    ElasticsearchStatusException(
                            "Create ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged",
                            RestStatus.INTERNAL_SERVER_ERROR
                    ))
                )
            }
        }

        private fun onUpdateMappingsResponse(response: AcknowledgedResponse) {
            if (response.isAcknowledged) {
                log.info("Updated  ${ScheduledJob.SCHEDULED_JOBS_INDEX} with mappings.")
                IndexUtils.scheduledJobIndexUpdated()
                prepareDestinationIndexing()
            } else {
                log.error("Update ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.")
                actionListener.onFailure(AlertingException.wrap(ElasticsearchStatusException(
                        "Updated ${ScheduledJob.SCHEDULED_JOBS_INDEX} mappings call not acknowledged.",
                        RestStatus.INTERNAL_SERVER_ERROR
                    ))
                )
            }
        }

        private fun updateDestination() {
            val getRequest = GetRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, request.destinationId)
            client.get(getRequest, object : ActionListener<GetResponse> {
                override fun onResponse(response: GetResponse) {
                    onGetResponse(response)
                }
                override fun onFailure(t: Exception) {
                    actionListener.onFailure(AlertingException.wrap(t))
                }
            })
        }

        private fun onGetResponse(response: GetResponse) {
            if (!response.isExists) {
                actionListener.onFailure(AlertingException.wrap(
                        ElasticsearchStatusException("Destination with ${request.destinationId} is not found", RestStatus.NOT_FOUND)))
                return
            }

            val destination = request.destination.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
            val indexRequest = IndexRequest(ScheduledJob.SCHEDULED_JOBS_INDEX)
                    .setRefreshPolicy(request.refreshPolicy)
                    .source(destination.toXContent(jsonBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                    .id(request.destinationId)
                    .setIfSeqNo(request.seqNo)
                    .setIfPrimaryTerm(request.primaryTerm)
                    .timeout(indexTimeout)

            client.index(indexRequest, object : ActionListener<IndexResponse> {
                override fun onResponse(response: IndexResponse) {
                    val failureReasons = checkShardsFailure(response)
                    if (failureReasons != null) {
                        actionListener.onFailure(
                                AlertingException.wrap(ElasticsearchStatusException(failureReasons.toString(), response.status())))
                        return
                    }
                    actionListener.onResponse(IndexDestinationResponse(response.id, response.version, response.seqNo,
                            response.primaryTerm, RestStatus.CREATED, destination))
                }
                override fun onFailure(t: Exception) {
                    actionListener.onFailure(AlertingException.wrap(t))
                }
            })
        }

        private fun checkShardsFailure(response: IndexResponse): String? {
            var failureReasons = StringBuilder()
            if (response.shardInfo.failed > 0) {
                response.shardInfo.failures.forEach {
                    entry -> failureReasons.append(entry.reason())
                }
                return failureReasons.toString()
            }
            return null
        }
    }
}
