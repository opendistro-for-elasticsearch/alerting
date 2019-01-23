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

package com.amazon.opendistro.util

import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.ShardSearchFailure
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus.BAD_GATEWAY
import org.elasticsearch.rest.RestStatus.GATEWAY_TIMEOUT
import org.elasticsearch.rest.RestStatus.SERVICE_UNAVAILABLE
import org.elasticsearch.script.ScriptException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/** Constructs an error message from an exception suitable for human consumption. */
fun Throwable.stackTraceString() : String {
    return if (this is ScriptException) {
        this.scriptStack.joinToString(separator = "\n", limit = 100)
    } else {
        StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()
    }
}

/** Convert an object to maps and lists representation */
fun ToXContent.convertToMap() : Map<String, Any> {
    val bytesReference = XContentHelper.toXContent(this, XContentType.JSON, false)
    return XContentHelper.convertToMap(bytesReference, false, XContentType.JSON).v2()
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

fun SearchResponse.firstFailureOrNull() : ShardSearchFailure? {
    return shardFailures?.getOrNull(0)
}

fun XContentParser.instant() : Instant? {
    return when {
        currentToken() == XContentParser.Token.VALUE_NULL -> null
        currentToken().isValue -> Instant.ofEpochMilli(longValue())
        else -> {
            XContentParserUtils.throwUnknownToken(currentToken(), tokenLocation)
            null // unreachable
        }
    }
}

fun XContentBuilder.optionalTimeField(name: String, instant: Instant?) : XContentBuilder {
    if (instant == null) {
        return nullField(name)
    }
    return ElasticAPI.INSTANCE.timeField(this, name, instant)
}

/**
 * Extension function for ES 6.3 that duplicates the ES 6.2 XContentBuilder.string() method.  On 6.2 this method shadows
 * the existing [XContentBuilder.string] method and so is not invoked.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun XContentBuilder.string() : String = ElasticAPI.INSTANCE.builderToBytesRef(this).utf8ToString()