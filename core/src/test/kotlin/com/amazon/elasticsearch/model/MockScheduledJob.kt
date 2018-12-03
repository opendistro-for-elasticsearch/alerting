package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import java.time.Instant

class MockScheduledJob(override val id: String,
                       override val version: Long,
                       override val name: String,
                       override val type: String,
                       override val enabled: Boolean,
                       override val schedule: Schedule,
                       override var lastUpdateTime: Instant,
                       override val enabledTime: Instant?) : ScheduledJob {
    override fun fromDocument(id: String, version: Long): ScheduledJob {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toXContent(builder: XContentBuilder?, params: ToXContent.Params?): XContentBuilder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}