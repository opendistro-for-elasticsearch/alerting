package com.amazon.elasticsearch.util

import com.amazon.elasticsearch.MonitoringPlugin
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
    return if (!userAgent.contains(MonitoringPlugin.KIBANA_USER_AGENT)) {
        FetchSourceContext(true, Strings.EMPTY_ARRAY, MonitoringPlugin.UI_METADATA_EXCLUDE)
    } else null
}

const val _ID = "_id"
const val _VERSION = "_version"
