/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.util

import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.StatusToXContentObject
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus.BAD_GATEWAY
import org.elasticsearch.rest.RestStatus.GATEWAY_TIMEOUT
import org.elasticsearch.rest.RestStatus.SERVICE_UNAVAILABLE


/**
 * Stupidly memory intensive way to convert a class to Map and Lists representation
 */
fun ToXContent.asMap(xContentRegistry: NamedXContentRegistry) : Map<String, Any> {
    val builder = XContentFactory.jsonBuilder()
    this.toXContent(builder, ToXContent.EMPTY_PARAMS)
    return XContentType.JSON.xContent().createParser(xContentRegistry, builder.bytes()).map()
}

/**
 * Backs off and retries a lambda that makes a request. This should not be called on any of the [standard][ThreadPool]
 * executors since those executors are not meant to be blocked by sleeping.
 */
fun <T> BackoffPolicy.retry(block : () -> T) : T {
    val iter = iterator()
    do {
        try {
            return block()
        } catch (e: ElasticsearchException) {
            if (iter.hasNext() && e.isRetriable()) {
                Thread.sleep(iter.next().millis)
            } else {
                throw e
            }
        }
    } while (true)
}

/**
 * Retries on 502, 503 and 504 per elastic client's behavior: https://github.com/elastic/elasticsearch-net/issues/2061
 * 429 must be retried manually as it's not clear if it's ok to retry for requests other than Bulk requests.
 */
fun ElasticsearchException.isRetriable() : Boolean {
    return (status() in listOf(BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT))
}

