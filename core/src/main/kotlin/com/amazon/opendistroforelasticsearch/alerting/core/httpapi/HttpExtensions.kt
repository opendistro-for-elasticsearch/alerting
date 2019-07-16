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
import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentType
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <C : HttpAsyncClient, T> C.suspendUntil(block: C.(FutureCallback<T>) -> Unit): T =
        suspendCancellableCoroutine { cont ->
            block(object : FutureCallback<T> {
                override fun cancelled() {
                    cont.resumeWith(Result.failure(CancellationException("Request cancelled")))
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
            NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, EntityUtils.toString(this.entity))
    return xcp.map()
}

fun HttpInput.toGetRequest(): HttpGet {
    val requestConfig = RequestConfig.custom()
            .setConnectTimeout(this.connection_timeout)
            .setSocketTimeout(this.socket_timeout)
            .build()
    // If url field is null or empty, construct an url field by field.
    val constructedUrl = if (Strings.isNullOrEmpty(this.url)) {
        val uriBuilder = URIBuilder()
        uriBuilder.scheme = this.scheme
        uriBuilder.host = this.host
        uriBuilder.port = this.port
        uriBuilder.path = this.path
        for (e in this.params.entries)
            uriBuilder.addParameter(e.key, e.value)
        uriBuilder.build().toString()
    } else {
        this.url
    }
    val httpGetRequest = HttpGet(constructedUrl)
    httpGetRequest.config = requestConfig
    return httpGetRequest
}
