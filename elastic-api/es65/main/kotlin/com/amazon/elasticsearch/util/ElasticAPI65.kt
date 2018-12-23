package com.amazon.elasticsearch.util

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.logging.Loggers
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.engine.Engine
import org.elasticsearch.index.shard.ShardId
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.InputStream
import java.time.Instant


class ElasticAPI65 : ElasticAPI() {
    override fun getLogger(clazz: Class<*>, settings: Settings): Logger = LogManager.getLogger(clazz)

    override fun getLogger(clazz: Class<*>, settings: Settings, shardId: ShardId): Logger =
            Loggers.getLogger(clazz, shardId)

    override fun jsonParser(xcr: NamedXContentRegistry, source: String): XContentParser =
            XContentType.JSON.xContent().createParser(xcr, LoggingDeprecationHandler.INSTANCE, source)

    override fun parseSearchSource(xcp: XContentParser): SearchSourceBuilder =
            SearchSourceBuilder.fromXContent(xcp, false)

    override fun jsonParser(xcr: NamedXContentRegistry, bytesRef: BytesReference) =
            XContentHelper.createParser(xcr, LoggingDeprecationHandler.INSTANCE, bytesRef, XContentType.JSON)

    override fun jsonParser(xcr: NamedXContentRegistry, istr: InputStream): XContentParser =
            XContentType.JSON.xContent().createParser(xcr, LoggingDeprecationHandler.INSTANCE, istr)

    override fun createParser(xcr: NamedXContentRegistry, bytesRef: BytesReference, xContentType: XContentType) : XContentParser =
            xContentType.xContent().createParser(xcr, LoggingDeprecationHandler.INSTANCE, bytesRef.streamInput())

    override fun hasWriteFailed(result: Engine.Result): Boolean = result.resultType != Engine.Result.Type.SUCCESS

    override fun timeField(xcb: XContentBuilder, fieldName: String, value: Instant): XContentBuilder =
            xcb.timeField(fieldName, fieldName, value.toEpochMilli())

    override fun builderToBytesRef(xcb: XContentBuilder): BytesReference = BytesReference.bytes(xcb)

    override fun getCreateIndexRequest(rr: RolloverRequest): CreateIndexRequest = rr.createIndexRequest
}
