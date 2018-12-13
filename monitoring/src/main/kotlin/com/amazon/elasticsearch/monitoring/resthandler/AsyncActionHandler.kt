package com.amazon.elasticsearch.monitoring.resthandler

import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel

abstract class AsyncActionHandler(protected val client: NodeClient, protected val channel: RestChannel) {

    protected fun onFailure(e: Exception) {
        channel.sendResponse(BytesRestResponse(channel, e))
    }
}