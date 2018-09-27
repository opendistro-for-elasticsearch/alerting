package com.amazon.elasticsearch.model

import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.search.SearchModule

interface XContentTestBase {
    fun builder(): XContentBuilder {
        return XContentBuilder.builder(XContentType.JSON.xContent())
    }

    fun parser(xc : String) : XContentParser {
        val parser = XContentType.JSON.xContent().createParser(xContentRegistry(), xc)
        parser.nextToken()
        return parser
    }

    fun xContentRegistry() : NamedXContentRegistry {
        return NamedXContentRegistry(listOf(SNSAction.XCONTENT_REGISTRY,
                SearchInput.XCONTENT_REGISTRY) +
                SearchModule(Settings.EMPTY, false, emptyList()).namedXContents)
    }
}