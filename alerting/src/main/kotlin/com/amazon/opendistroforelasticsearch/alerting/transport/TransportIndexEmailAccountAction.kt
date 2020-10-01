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

import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailAccountAction
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailAccountRequest
import com.amazon.opendistroforelasticsearch.alerting.action.IndexEmailAccountResponse
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOB_TYPE
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.INDEX_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings.Companion.REQUEST_TIMEOUT
import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
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
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

private val log = LogManager.getLogger(TransportIndexEmailAccountAction::class.java)

class TransportIndexEmailAccountAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters,
    val scheduledJobIndices: ScheduledJobIndices,
    val clusterService: ClusterService,
    settings: Settings,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<IndexEmailAccountRequest, IndexEmailAccountResponse>(
    IndexEmailAccountAction.NAME, transportService, actionFilters, ::IndexEmailAccountRequest
) {

    @Volatile private var requestTimeout = REQUEST_TIMEOUT.get(settings)
    @Volatile private var indexTimeout = INDEX_TIMEOUT.get(settings)

    init {
        clusterService.clusterSettings.addSettingsUpdateConsumer(REQUEST_TIMEOUT) { requestTimeout = it }
        clusterService.clusterSettings.addSettingsUpdateConsumer(INDEX_TIMEOUT) { indexTimeout = it }
    }

    override fun doExecute(task: Task, request: IndexEmailAccountRequest, actionListener: ActionListener<IndexEmailAccountResponse>) {
        client.threadPool().threadContext.stashContext().use {
            IndexEmailAccountHandler(client, actionListener, request).start()
        }
    }

    inner class IndexEmailAccountHandler(
        private val client: Client,
        private val actionListener: ActionListener<IndexEmailAccountResponse>,
        private val request: IndexEmailAccountRequest
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
                prepareEmailAccountIndexing()
            }
        }

        private fun prepareEmailAccountIndexing() {

            val query = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery(
                    "${EmailAccount.EMAIL_ACCOUNT_TYPE}.${EmailAccount.NAME_FIELD}.keyword", request.emailAccount.name)
                )
                .filter(QueryBuilders.existsQuery(EmailAccount.EMAIL_ACCOUNT_TYPE))
            val searchSource = SearchSourceBuilder().query(query).timeout(requestTimeout)
            val searchRequest = SearchRequest(SCHEDULED_JOBS_INDEX).source(searchSource)
            client.search(searchRequest, object : ActionListener<SearchResponse> {
                override fun onResponse(searchResponse: SearchResponse) {
                    onSearchResponse(searchResponse)
                }

                override fun onFailure(e: Exception) {
                    actionListener.onFailure(e)
                }
            })
        }

        /**
         * After searching for all existing email accounts with the same name as the one in the request
         * we validate if the name has already been used or not.
         */
        private fun onSearchResponse(response: SearchResponse) {
            if (request.method == RestRequest.Method.POST) {
                // For a request to create a new email account, check if the name exists for another email account
                val totalHits = response.hits.totalHits?.value
                if (totalHits != null && totalHits > 0) {
                    log.error("Unable to create email group with name=[${request.emailAccount.name}] because name is already in use.")
                    actionListener.onFailure(
                        IllegalArgumentException(
                            "Unable to create email group with name=[${request.emailAccount.name}] because name is already in use."
                        )
                    )
                    return
                } else {
                    indexEmailAccount()
                }
            } else {
                // For an update request, check if the name is being used by an email account other than the one being updated
                for (hit in response.hits) {
                    if (hit.id != request.emailAccountID) {
                        log.error("Unable to update email group with name=[${request.emailAccount.name}] because name is already in use.")
                        actionListener.onFailure(
                            IllegalArgumentException(
                                "Unable to update email group with name=[${request.emailAccount.name}] because name is already in use."
                            )
                        )
                        // Return early if name is invalid
                        return
                    }
                }

                // Call update if name is valid
                updateEmailAccount()
            }
        }

        private fun onCreateMappingsResponse(response: CreateIndexResponse) {
            if (response.isAcknowledged) {
                log.info("Created $SCHEDULED_JOBS_INDEX with mappings.")
                prepareEmailAccountIndexing()
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
                prepareEmailAccountIndexing()
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

        private fun indexEmailAccount(update: Boolean = false) {
            request.emailAccount = request.emailAccount.copy(schemaVersion = IndexUtils.scheduledJobIndexSchemaVersion)
            var indexRequest = IndexRequest(SCHEDULED_JOBS_INDEX)
                    .setRefreshPolicy(request.refreshPolicy)
                    .source(request.emailAccount.toXContent(jsonBuilder(), ToXContent.MapParams(mapOf("with_type" to "true"))))
                    .setIfSeqNo(request.seqNo)
                    .setIfPrimaryTerm(request.primaryTerm)
                    .timeout(indexTimeout)

            // If request is to update, then add id to index request
            if (update) indexRequest = indexRequest.id(request.emailAccountID)

            client.index(indexRequest, object : ActionListener<IndexResponse> {
                override fun onResponse(response: IndexResponse) {
                    val failureReasons = checkShardsFailure(response)
                    if (failureReasons != null) {
                        actionListener.onFailure(ElasticsearchStatusException(failureReasons.toString(), response.status()))
                        return
                    }
                    actionListener.onResponse(
                        IndexEmailAccountResponse(response.id, response.version, response.seqNo, response.primaryTerm,
                            RestStatus.CREATED, request.emailAccount)
                    )
                }

                override fun onFailure(e: Exception) {
                    actionListener.onFailure(e)
                }
            })
        }

        private fun updateEmailAccount() {
            val getRequest = GetRequest(SCHEDULED_JOBS_INDEX, request.emailAccountID)
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
                    ElasticsearchStatusException("EmailAccount with ${request.emailAccountID} was not found", RestStatus.NOT_FOUND)
                )
                return
            }

            indexEmailAccount(update = true)
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
