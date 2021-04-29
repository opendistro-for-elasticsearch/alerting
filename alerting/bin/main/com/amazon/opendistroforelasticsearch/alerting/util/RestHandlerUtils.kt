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

package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import org.elasticsearch.common.Strings
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.search.fetch.subphase.FetchSourceContext

/**
 * Checks to see if the request came from Kibana, if so we want to return the UI Metadata from the document.
 * If the request came from the client then we exclude the UI Metadata from the search result.
 *
 * @param request
 * @return FetchSourceContext
 */
fun context(request: RestRequest): FetchSourceContext? {
    val userAgent = Strings.coalesceToEmpty(request.header("User-Agent"))
    return if (!userAgent.contains(AlertingPlugin.KIBANA_USER_AGENT)) {
        FetchSourceContext(true, Strings.EMPTY_ARRAY, AlertingPlugin.UI_METADATA_EXCLUDE)
    } else null
}

const val _ID = "_id"
const val _VERSION = "_version"
const val _SEQ_NO = "_seq_no"
const val IF_SEQ_NO = "if_seq_no"
const val _PRIMARY_TERM = "_primary_term"
const val IF_PRIMARY_TERM = "if_primary_term"
const val REFRESH = "refresh"
