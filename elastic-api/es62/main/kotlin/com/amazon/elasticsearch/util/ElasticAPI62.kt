package com.amazon.elasticsearch.util

import org.apache.logging.log4j.Logger
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.logging.ServerLoggers
import org.elasticsearch.common.settings.Settings
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


class ElasticAPI62 : ElasticAPI() {
    override fun getLogger(clazz: Class<*>, settings: Settings): Logger =
            ServerLoggers.getLogger(clazz, settings)

    override fun getLogger(clazz: Class<*>, settings: Settings, shardId: ShardId): Logger =
            ServerLoggers.getLogger(clazz, settings, shardId)

    override fun jsonParser(xcr: NamedXContentRegistry, source: String): XContentParser =
            XContentType.JSON.xContent().createParser(xcr, source)

    override fun jsonParser(xcr: NamedXContentRegistry, bytesRef: BytesReference) =
            XContentHelper.createParser(xcr, bytesRef, XContentType.JSON)

    override fun jsonParser(xcr: NamedXContentRegistry, istr: InputStream): XContentParser =
            XContentType.JSON.xContent().createParser(xcr, istr)

    override fun createParser(xcr: NamedXContentRegistry, bytesRef: BytesReference,
                              xContentType: XContentType): XContentParser =
            xContentType.xContent().createParser(xcr, bytesRef)

    override fun hasWriteFailed(result: Engine.Result): Boolean = result.hasFailure()

    override fun timeField(xcb: XContentBuilder, fieldName: String, value: Instant) : XContentBuilder =
            xcb.dateField(fieldName, fieldName, value.toEpochMilli())

    override fun parseSearchSource(xcp: XContentParser): SearchSourceBuilder =
            SearchSourceBuilder.fromXContent(xcp)

    override fun builderToBytesRef(xcb: XContentBuilder): BytesReference = xcb.bytes()

    override fun getCreateIndexRequest(rr: RolloverRequest) : CreateIndexRequest =
            CreateIndexRequest().also { rr.setCreateIndexRequest(it) }

}
