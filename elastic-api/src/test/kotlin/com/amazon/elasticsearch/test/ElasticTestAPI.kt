package com.amazon.elasticsearch.test

import org.apache.http.Header
import org.apache.http.HttpEntity
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient

/**
 * Wrapper for [RestClient.performRequest] which was deprecated in ES 6.5 and is used in tests. This provides
 * a single place to suppress deprecation warnings. This will probably need further work when the API is removed entirely
 * but that's an exercise for another day.
 */
@Suppress("DEPRECATION")
fun RestClient.makeRequest(method: String, endpoint: String, params: Map<String, String> = emptyMap(),
                           entity: HttpEntity? = null, vararg headers: Header) : Response {
    return if (entity != null) {
        performRequest(method, endpoint, params, entity, *headers)
    } else {
        performRequest(method, endpoint, params, *headers)
    }
}

/**
 * Wrapper for [RestClient.performRequest] which was deprecated in ES 6.5 and is used in tests. This provides
 * a single place to suppress deprecation warnings. This will probably need further work when the API is removed entirely
 * but that's an exercise for another day.
 */
@Suppress("DEPRECATION")
fun RestClient.makeRequest(method: String, endpoint: String, entity: HttpEntity? = null, vararg headers: Header)
        : Response {
    return if (entity != null) {
        performRequest(method, endpoint, emptyMap(), entity, *headers)
    } else {
        performRequest(method, endpoint, emptyMap(), *headers)
    }
}
