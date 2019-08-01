package com.amazon.opendistroforelasticsearch.alerting.core.httpapi

import com.amazon.opendistroforelasticsearch.alerting.core.model.HttpInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.concurrent.FutureCallback
import org.apache.http.nio.client.HttpAsyncClient
import org.apache.http.util.EntityUtils
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentType
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <C : HttpAsyncClient, T> C.suspendUntil(block: C.(FutureCallback<T>) -> Unit): T =
        suspendCancellableCoroutine { cont ->
            block(object : FutureCallback<T> {
                override fun cancelled() {
                    cont.resumeWithException(CancellationException("Request cancelled"))
                }

                override fun completed(result: T) {
                    cont.resume(result)
                }

                override fun failed(ex: Exception) {
                    cont.resumeWithException(ex)
                }
            })
        }

fun HttpResponse.toMap(): Map<String, Any> {
    val xcp = XContentType.JSON.xContent().createParser(
            NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, EntityUtils.toString(entity))
    return xcp.map()
}

fun HttpInput.toGetRequest(): HttpGet {
    // Change timeout values to settings specified from input, multiply by 1000 to convert to milliseconds.
    val requestConfig = RequestConfig.custom()
            .setConnectTimeout(this.connection_timeout * 1000)
            .setSocketTimeout(this.socket_timeout * 1000)
            .build()
    val constructedUrl = this.toConstructedUrl().toString()
    val httpGetRequest = HttpGet(constructedUrl)
    httpGetRequest.config = requestConfig
    return httpGetRequest
}

/**
 * Construct url either by url or by scheme+host+port+path+params.
 */
fun HttpInput.toConstructedUrl(): URI {
    return if (url.isEmpty()) {
        val uriBuilder = URIBuilder()
        uriBuilder.scheme = scheme
        uriBuilder.host = host
        uriBuilder.port = port
        uriBuilder.path = path
        for (e in params.entries)
            uriBuilder.addParameter(e.key, e.value)
        uriBuilder.build()
    } else {
        URIBuilder(url).build()
    }
}
