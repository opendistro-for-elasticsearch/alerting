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

import org.apache.logging.log4j.Logger
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.engine.Engine
import org.elasticsearch.index.shard.ShardId
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.InputStream
import java.time.Instant
import java.util.ServiceLoader

/**
 * Wrapper to abstract away changes in plugin API between elastic versions. The actual implementation lives in the ES version
 * specific module (e.g. es65) and is loaded via a [ServiceLoader] at runtime.
 */
abstract class ElasticAPI {

    companion object {
        @JvmStatic val INSTANCE: ElasticAPI by lazy {
            val loader = ServiceLoader.load(ElasticAPI::class.java, ElasticAPI::class.java.classLoader)
            loader.first() // There must always be an instance on the classpath
        }
    }

    /**
     * Function moved from [ServerLoggers] in ES 6.2 to [Loggers] in ES 6.3
     */
    abstract fun getLogger(clazz: Class<*>, settings: Settings): Logger

    /**
     * Function moved from [ServerLoggers] in ES 6.2 to [Loggers] in ES 6.3
     */
    abstract fun getLogger(clazz: Class<*>, settings: Settings, shardId: ShardId): Logger

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun jsonParser(xcr: NamedXContentRegistry, source: String): XContentParser

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun jsonParser(xcr: NamedXContentRegistry, bytesRef: BytesReference): XContentParser

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun jsonParser(xcr: NamedXContentRegistry, istr: InputStream): XContentParser

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun createParser(xcr: NamedXContentRegistry, bytesRef: BytesReference, xContentType: XContentType): XContentParser

    /**
     * [Engine.Result] has a ResultType enum in ES 6.3 but not in 6.2
     */
    abstract fun hasWriteFailed(result: Engine.Result): Boolean

    /**
     * [XContentBuilder.dateField]  method in ES 6.2 renamed to [XContentBuilder.timeField] in ES 6.3
     */
    abstract fun timeField(xcb: XContentBuilder, fieldName: String, value: Instant): XContentBuilder

    /**
     * [XContentBuilder.toBytes] method in ES 6.2 moved to [BytesReference.bytes] in ES 6.3
     */
    abstract fun builderToBytesRef(xcb: XContentBuilder): BytesReference

    /**
     * [RolloverRequest.getCreateIndexRequest] is public in ES 6.3 but not in 6.2
     */
    abstract fun getCreateIndexRequest(rr: RolloverRequest): CreateIndexRequest

    /**
     * Elastic consumes trailing tokens at the end of a search source by default in ES >= 6.3.
     * See [ES migration doc](https://github.com/elastic/elasticsearch/blob/6.3/docs/reference/migration/migrate_6_0/search.asciidoc#invalid-_search-request-body)
     */
    abstract fun parseSearchSource(xcp: XContentParser): SearchSourceBuilder
}
