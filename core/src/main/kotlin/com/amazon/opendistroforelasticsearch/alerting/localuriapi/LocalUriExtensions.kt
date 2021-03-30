package com.amazon.opendistroforelasticsearch.alerting.localuriapi

import com.amazon.opendistroforelasticsearch.alerting.core.model.LocalUriInput
import org.apache.http.client.utils.URIBuilder
import java.net.URI

/**
 * Construct url either by url or by scheme+host+port+path+params.
 */
fun LocalUriInput.toConstructedUrl(): URI {
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