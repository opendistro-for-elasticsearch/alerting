/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.transport

import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailGroupAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailGroupRequest
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailGroupResponse
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
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
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

private val log = LogManager.getLogger(TransportIndexEmailGroupAction::class.java)

class TransportIndexEmailGroupAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val scheduledJobIndices: ScheduledJobIndices,
    val clusterService: ClusterService,
    settings: Settings,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<IndexEmailGroupRequest, IndexEmailGroupResponse>(
    IndexEmailGroupAction.NAME, transportService, actionFilters, ::IndexEmailGroupRequest
) {

    @Volatile private var indexTimeout = AlertingSettings.INDEX_TIMEOUT.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(AlertingSettings.INDEX_TIMEOUT) { indexTimeout = it }
    }

    override fun doExecute(task: Task, request: IndexEmailGroupRequest, actionListener: ActionListener<IndexEmailGroupResponse>) {
        client.threadPool().threadContext.stashContext().use {
            IndexEmailGroupHandler(client, actionListener, request).start()
        }
    }

    inner class IndexEmailGroupHandler(
        private val client: Client,
        private val actionListener: ActionListener<IndexEmailGroupResponse>,
        private val request: IndexEmailGroupRequest
    ) {

        fun start() {
            if (!scheduledJobIndices.scheduledJobIndexExists()) {
                scheduledJobIndices.initScheduledJobIndex(object : ActionListener<CreateIndexResponse> {
                    override fun onResponse(response: CreateIndexResponse) {
                        onCreateMappingsResponse(response)
                    }

                    override fun onFailure(e: Exception) {
                        actionListener.onFailure(e)
                    }
                })
            } else if (!IndexUtils.scheduledJobIndexUpdated) {
                IndexUtils.updateIndexMapping(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE,
                    ScheduledJobIndices.scheduledJobMappings(), clusterService.state(), client.admin().indices(),
                    object : ActionListener<AcknowledgedResponse> {
                        override fun onResponse(response: AcknowledgedResponse) {
                            onUpdateMappingsResponse(response)
                        }

                        override fun onFailure(e: Exception) {
                            actionListener.onFailure(e)
                        }
                    })
            } else {
                prepareEmailGroupIndexing()
            }
        }

        private fun prepareEmailGroupIndexing() {
            if (request.method == RestRequest.Method.PUT) {
                updateEmailGroup()
            } else {
                indexEmailGroup()
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                log.info("Created $SCHEDULED_JOBS_INDEX with mappings.")
                prepareEmailGroupIndexing()
                IndexUtils.scheduledJobIndexUpdated()
            } else {
                log.error("Create $SCHEDULED_JOBS_INDEX mappings call not acknowledged.")
                actionListener.onFailure(
                    ElasticsearchStatusException(
                        "Create $SCHEDULED_JOBS_INDEX mappings call not acknowledged.",
                        RestStatus.INTERNAL_SERVER_ERROR
                    )
                )
            }
        }

        private fun onUpdateMappingsResponse(response: AcknowledgedResponse) {
            if (response.isAcknowledged) {
                log.info("Updated $SCHEDULED_JOBS_INDEX with mappings.")
                IndexUtils.scheduledJobIndexUpdated()
                prepareEmailGroupIndexing()
            } else {
                log.error("Update $SCHEDULED_JOBS_INDEX mappings call not acknowledged.")
                actionListener.onFailure(
                    ElasticsearchStatusException(
                        "Update $SCHEDULED_JOBS_INDEX mappings call not acknowledged.",
                        RestStatus.INTERNAL_SERVER_ERROR
                    )
                )
            }
        }

        private fun indexEmailGroup(update: Boolean = false) {
            request.emailGroup = request.emailGroup.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
            var indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                .setRefreshPolicy(request.refreshPolicy)
                .source(request.emailGroup.toXContent(jsonBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                .setIfSeqNo(request.seqNo)
                .setIfPrimaryTerm(request.primaryTerm)
                .timeout(indexTimeout)

            // If request is to update, then add id to index request
            if (update) indexRequest = indexRequest.id(request.emailGroupID)

            client.index(indexRequest, object : ActionListener<IndexResponse> {
                override fun onResponse(response: IndexResponse) {
                    val failureReasons = checkShardsFailure(response)
                    if (failureReasons != null) {
                        actionListener.onFailure(ElasticsearchStatusException(failureReasons.toString(), response.status()))
                        return
                    }
                    actionListener.onResponse(
                        IndexEmailGroupResponse(response.id, response.version, response.seqNo, response.primaryTerm,
                            RestStatus.CREATED, request.emailGroup)
                    )
                }

                override fun onFailure(e: Exception) {
                    actionListener.onFailure(e)
                }
            })
        }

        private fun updateEmailGroup() {
            val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, request.emailGroupID)
            client.get(getRequest, object : ActionListener<GetResponse> {
                override fun onResponse(response: GetResponse) {
                    onGetResponse(response)
                }

                override fun onFailure(e: Exception) {
                    actionListener.onFailure(e)
                }
            })
        }

        private fun onGetResponse(response: GetResponse) {
            if (!response.isExists) {
                actionListener.onFailure(
                    ElasticsearchStatusException("EmailGroup with ${request.emailGroupID} was not found", RestStatus.NOT_FOUND)
                )
                return
            }

            indexEmailGroup(update = true)
        }

        private fun checkShardsFailure(response: IndexResponse): String? {
            val failureReasons = StringBuilder()
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
