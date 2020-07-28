package com.amazon.opendistroforelasticsearch.alerting.model.destination.email

import com.amazon.opendistroforelasticsearch.alerting.util.IndexUtils.Companion.NO_SCHEMA_VERSION
import org.elasticsearch.common.settings.SecureString
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.util.Locale

/**
 * A value object that represents an Email Account. Email Accounts contain the configuration
 * information for sender emails when sending email messages through the Email destination.
 */
data class EmailAccount(
    val id: String = NO_ID,
    val schemaVersion: Int = NO_SCHEMA_VERSION,
    val name: String,
    val email: String,
    val host: String,
    val port: Int,
    val method: MethodType,
    val username: SecureString? = null,
    val password: SecureString? = null
) : ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
        if (params.paramAsBoolean("with_type", false)) builder.startObject(EMAIL_ACCOUNT_TYPE)
        builder.field(SCHEMA_VERSION, schemaVersion)
                .field(NAME_FIELD, name)
                .field(EMAIL_FIELD, email)
                .field(HOST_FIELD, host)
                .field(PORT_FIELD, port)
                .field(METHOD_FIELD, method.value)
        if (params.paramAsBoolean("with_type", false)) builder.endObject()
        return builder.endObject()
    }

    fun toXContent(builder: XContentBuilder): XContentBuilder {
        return toXContent(builder, ToXContent.EMPTY_PARAMS)
    }

    enum class MethodType(val value: String) {
        NONE("none"),
        SSL("ssl"),
        TLS("starttls")
    }

    companion object {
        const val EMAIL_ACCOUNT_TYPE = "email_account"
        const val NO_ID = ""
        const val SCHEMA_VERSION = "schema_version"
        const val NAME_FIELD = "name"
        const val EMAIL_FIELD = "email"
        const val HOST_FIELD = "host"
        const val PORT_FIELD = "port"
        const val METHOD_FIELD = "method"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID): EmailAccount {
            var schemaVersion = NO_SCHEMA_VERSION
            lateinit var name: String
            lateinit var email: String
            lateinit var host: String
            var port: Int = -1
            lateinit var method: String

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    SCHEMA_VERSION -> schemaVersion = xcp.intValue()
                    NAME_FIELD -> name = xcp.text()
                    EMAIL_FIELD -> email = xcp.text()
                    HOST_FIELD -> host = xcp.text()
                    PORT_FIELD -> port = xcp.intValue()
                    METHOD_FIELD -> {
                        method = xcp.text()
                        val allowedMethods = MethodType.values().map { it.value }
                        if (!allowedMethods.contains(method)) {
                            throw IllegalStateException("Method should be one of $allowedMethods")
                        }
                    }
                }
            }

            return EmailAccount(id,
                    schemaVersion,
                    name,
                    email,
                    host,
                    port,
                    MethodType.valueOf(method.toUpperCase(Locale.ROOT))
            )
        }
    }
}
