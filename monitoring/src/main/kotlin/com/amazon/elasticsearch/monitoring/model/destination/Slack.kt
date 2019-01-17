package com.amazon.elasticsearch.monitoring.model.destination

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * A value object that represents a Slack message. Slack message will be
 * submitted to the Slack destination
 */
data class Slack(val url: String) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(TYPE)
                .field(URL, url)
                .endObject()
    }

    companion object {
        const val URL = "url"
        const val TYPE = "slack"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): Slack {
            lateinit var url: String

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    URL -> url = xcp.text()
                }
            }
            return Slack(url = requireNotNull(url) { "URL is null" })
        }
    }
}
