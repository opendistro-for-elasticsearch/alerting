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

package com.amazon.opendistroforelasticsearch.alerting.elasticapi

import kotlinx.coroutines.delay
import org.apache.logging.log4j.Logger
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.ShardSearchFailure
import org.elasticsearch.client.ElasticsearchClient
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.RestStatus.BAD_GATEWAY
import org.elasticsearch.rest.RestStatus.GATEWAY_TIMEOUT
import org.elasticsearch.rest.RestStatus.SERVICE_UNAVAILABLE
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.ThreadContextElement
import org.elasticsearch.common.util.concurrent.ThreadContext
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext
import kotlin.coroutines.CoroutineContext

/** Convert an object to maps and lists representation */
fun ToXContent.convertToMap(): Map<String, Any> {
    val bytesReference = XContentHelper.toXContent(this, XContentType.JSON, false)
    return XContentHelper.convertToMap(bytesReference, false, XContentType.JSON).v2()
}

/**
 * Backs off and retries a lambda that makes a request. This should not be called on any of the [standard][ThreadPool]
 * executors since those executors are not meant to be blocked by sleeping.
 */
fun <T> BackoffPolicy.retry(block: () -> T): T {
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
 * Retries the given [block] of code as specified by the receiver [BackoffPolicy], if [block] throws an [ElasticsearchException]
 * that is retriable (502, 503, 504).
 *
 * If all retries fail the final exception will be rethrown. Exceptions caught during intermediate retries are
 * logged as warnings to [logger]. Similar to [org.elasticsearch.action.bulk.Retry], except this retries on
 * 502, 503, 504 error codes as well as 429.
 *
 * @param logger - logger used to log intermediate failures
 * @param retryOn - any additional [RestStatus] values that should be retried
 * @param block - the block of code to retry. This should be a suspend function.
 */
suspend fun <T> BackoffPolicy.retry(
    logger: Logger,
    retryOn: List<RestStatus> = emptyList(),
    block: suspend () -> T
): T {
    val iter = iterator()
    do {
        try {
            return block()
        } catch (e: ElasticsearchException) {
            if (iter.hasNext() && (e.isRetriable() || retryOn.contains(e.status()))) {
                val backoff = iter.next()
                logger.warn("Operation failed. Retrying in $backoff.", e)
                delay(backoff.millis)
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
fun ElasticsearchException.isRetriable(): Boolean {
    return (status() in listOf(BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT))
}

fun SearchResponse.firstFailureOrNull(): ShardSearchFailure? {
    return shardFailures?.getOrNull(0)
}

fun XContentParser.instant(): Instant? {
    return when {
        currentToken() == XContentParser.Token.VALUE_NULL -> null
        currentToken().isValue -> Instant.ofEpochMilli(longValue())
        else -> {
            XContentParserUtils.throwUnknownToken(currentToken(), tokenLocation)
            null // unreachable
        }
    }
}

fun XContentBuilder.optionalTimeField(name: String, instant: Instant?): XContentBuilder {
    if (instant == null) {
        return nullField(name)
    }
    return this.timeField(name, name, instant.toEpochMilli())
}

/**
 * Extension function for ES 6.3 and above that duplicates the ES 6.2 XContentBuilder.string() method.
 */
fun XContentBuilder.string(): String = BytesReference.bytes(this).utf8ToString()

/**
 * Converts [ElasticsearchClient] methods that take a callback into a kotlin suspending function.
 *
 * @param block - a block of code that is passed an [ActionListener] that should be passed to the ES client API.
 */
suspend fun <C : ElasticsearchClient, T> C.suspendUntil(block: C.(ActionListener<T>) -> Unit): T =
    suspendCoroutine { cont ->
        block(object : ActionListener<T> {
            override fun onResponse(response: T) = cont.resume(response)

            override fun onFailure(e: Exception) = cont.resumeWithException(e)
        })
    }

/**
 * Store a [ThreadContext] and restore a [ThreadContext] when the coroutine resumes on a different thread.
 *
 * @param threadContext - a [ThreadContext] instance
 */
class ElasticThreadContextElement(private val threadContext: ThreadContext) : ThreadContextElement<Unit> {

    companion object Key : CoroutineContext.Key<ElasticThreadContextElement>
    private var context: StoredContext = threadContext.newStoredContext(true)

    override val key: CoroutineContext.Key<*>
        get() = Key

    override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) {
        this.context = threadContext.stashContext()
    }

    override fun updateThreadContext(context: CoroutineContext) = this.context.close()
}
