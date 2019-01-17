package com.amazon.elasticsearch.monitoring.model.destination

import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * A value object that represents a Custom webhook message. Webhook message will be
 * submitted to the Custom webhook destination
 */
data class CustomWebhook(val url: String?, val scheme: String?, val host: String?, val port: Int, val path: String?,
                         val queryParams: Map<String, String>, val headerParams: Map<String, String>,
                         val username: String?, val password: String?) : ToXContent {

    init {
        require (!(Strings.isNullOrEmpty(url) && Strings.isNullOrEmpty(host))) {
            "Url or Host name must be provided."
        }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject(TYPE)
                .field(URL, url)
                .field(SCHEME_FIELD, scheme)
                .field(HOST_FIELD, host)
                .field(PORT_FIELD, port)
                .field(PATH_FIELD, path)
                .field(QUERYPARAMS_FIELD, queryParams)
                .field(HEADERPARAMS_FIELD, headerParams)
                .field(USERNAME_FIELD, username)
                .field(PASSWORD_FIELD, password)
                .endObject()
    }

    companion object {
        const val URL = "url"
        const val TYPE = "custom_webhook"
        const val SCHEME_FIELD = "scheme"
        const val HOST_FIELD = "host"
        const val PORT_FIELD = "port"
        const val PATH_FIELD = "path"
        const val QUERYPARAMS_FIELD = "query_params"
        const val HEADERPARAMS_FIELD = "header_params"
        const val USERNAME_FIELD = "username"
        const val PASSWORD_FIELD = "password"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): CustomWebhook {
            var url: String? = null
            var scheme: String? = null
            var host: String? = null
            var port: Int = -1
            var path: String? = null
            var queryParams: Map<String, String> = mutableMapOf()
            var headerParams: Map<String, String> = mutableMapOf()
            var username: String? = null
            var password: String? = null

            ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    URL -> url = xcp.textOrNull()
                    SCHEME_FIELD -> scheme = xcp.textOrNull()
                    HOST_FIELD -> host = xcp.textOrNull()
                    PORT_FIELD -> port = xcp.intValue()
                    PATH_FIELD -> path = xcp.textOrNull()
                    QUERYPARAMS_FIELD ->  queryParams = xcp?.mapStrings()
                    HEADERPARAMS_FIELD -> headerParams = xcp?.mapStrings()
                    USERNAME_FIELD -> username = xcp.textOrNull()
                    PASSWORD_FIELD -> password = xcp.textOrNull()
                }
            }
            return CustomWebhook(url, scheme, host, port, path, queryParams, headerParams, username, password)
        }
    }
}
