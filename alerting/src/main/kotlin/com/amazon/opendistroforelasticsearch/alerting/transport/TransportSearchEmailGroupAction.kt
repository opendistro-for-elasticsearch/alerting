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

import com.amazon.opendistroforelasticsearch.alerting.action.SearchEmailGroupAction
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService

class TransportSearchEmailGroupAction @Inject constructor(
    transportService: TransportService,
    val client: Client,
    actionFilters: ActionFilters
) : HandledTransportAction<SearchRequest, SearchResponse>(
    SearchEmailGroupAction.NAME, transportService, actionFilters, ::SearchRequest
) {

    override fun doExecute(task: Task, searchRequest: SearchRequest, actionListener: ActionListener<SearchResponse>) {
        client.threadPool().threadContext.stashContext().use {
            client.search(searchRequest, object : ActionListener<SearchResponse> {
                override fun onResponse(response: SearchResponse) {
                    actionListener.onResponse(response)
                }

                override fun onFailure(e: Exception) {
                    actionListener.onFailure(e)
                }
            })
        }
    }
}
