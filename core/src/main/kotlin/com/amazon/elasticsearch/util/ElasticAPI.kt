package com.amazon.elasticsearch.util

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
 * specific module (e.g. es62, es63) and is loaded via a [ServiceLoader] at runtime.
 */
abstract class ElasticAPI {

    companion object {
        @JvmStatic val INSTANCE : ElasticAPI by lazy {
            val loader = ServiceLoader.load(ElasticAPI::class.java, ElasticAPI::class.java.classLoader)
            loader.findFirst().get() // There must always be an instance on the classpath
        }
    }

    /**
     * Function moved from [ServerLoggers] in ES 6.2 to [Loggers] in ES 6.3
     */
    abstract fun getLogger(clazz: Class<*>, settings: Settings) : Logger

    /**
     * Function moved from [ServerLoggers] in ES 6.2 to [Loggers] in ES 6.3
     */
    abstract fun getLogger(clazz: Class<*>, settings: Settings, shardId: ShardId): Logger

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun jsonParser(xcr: NamedXContentRegistry, source: String) : XContentParser

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun jsonParser(xcr : NamedXContentRegistry, bytesRef: BytesReference) : XContentParser

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun jsonParser(xcr : NamedXContentRegistry, istr: InputStream) : XContentParser

    /**
     * [XContent.createParser] takes a [DeprecationHandler] param in ES 6.3
     */
    abstract fun createParser(xcr: NamedXContentRegistry, bytesRef: BytesReference, xContentType: XContentType): XContentParser

    /**
     * [Engine.Result] has a ResultType enum in ES 6.3 but not in 6.2
     */
    abstract fun hasWriteFailed(result : Engine.Result) : Boolean

    /**
     * [XContentBuilder.dateField]  method in ES 6.2 renamed to [XContentBuilder.timeField] in ES 6.3
     */
    abstract fun timeField(xcb: XContentBuilder, fieldName: String, value: Instant): XContentBuilder

    /**
     * [XContentBuilder.toBytes] method in ES 6.2 moved to [BytesReference.bytes] in ES 6.3
     */
    abstract fun builderToBytesRef(xcb: XContentBuilder) : BytesReference

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