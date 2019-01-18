package com.amazon.elasticsearch.model.action

import com.amazon.elasticsearch.model.action.Action.Companion.MUSTACHE
import com.amazon.elasticsearch.util.string
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.Script
import java.io.IOException
import java.lang.IllegalStateException


/**
 * This class holds the data and parser for Chime message which will be
 * submitted to the Chime destination
 */
data class ChimeAction(override val name: String,
                       val destinationId: String,
                       val subjectTemplate: Script,
                       val messageTemplate: Script,
                       override val type: String = CHIME_TYPE) : Action {

    init {
        require(subjectTemplate.lang == MUSTACHE) { "subject_template must be a mustache script" }
        require(messageTemplate.lang == MUSTACHE) { "message_template must be a mustache script" }
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(type)
                .field(NAME_FIELD, name)
                .field(DESTINATION_ID_FIELD, destinationId)
                .field(SUBJECT_TEMPLATE_FIELD, subjectTemplate)
                .field(MESSAGE_TEMPLATE_FIELD, messageTemplate)
                .endObject()
                .endObject()
    }

    override fun asTemplateArg(): Map<String, Any> {
        return mapOf(NAME_FIELD to name, TYPE_FIELD to type)
    }

    companion object {
        const val NAME_FIELD = "name"
        const val TYPE_FIELD = "type"
        const val DESTINATION_ID_FIELD = "destination_id"
        const val SUBJECT_TEMPLATE_FIELD = "subject_template"
        const val MESSAGE_TEMPLATE_FIELD = "message_template"
        const val CHIME_TYPE = "chime"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Action::class.java,
                ParseField(CHIME_TYPE),
                CheckedFunction { parseInner(it) })

        @JvmStatic @Throws(IOException::class)
        private fun parseInner(xcp: XContentParser): ChimeAction {
            lateinit var name: String
            lateinit var destinationId: String
            lateinit var subjectTemplate: Script
            lateinit var messageTemplate: Script

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while(xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    NAME_FIELD -> name = xcp.textOrNull()
                    DESTINATION_ID_FIELD -> destinationId = xcp.textOrNull()
                    SUBJECT_TEMPLATE_FIELD -> subjectTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                    MESSAGE_TEMPLATE_FIELD -> messageTemplate = Script.parse(xcp, Script.DEFAULT_TEMPLATE_LANG)
                }
            }

            return ChimeAction(requireNotNull(name) { "Chime Action name is null" },
                    requireNotNull(destinationId) { "Chime Action destinationId is null" },
                    requireNotNull(subjectTemplate) { "Chime Action subject_template is null" },
                    requireNotNull(messageTemplate) { "Chime Action message_temaplate is null" })
        }
    }

    fun constructMessageContent(subject: String?, message: String?, destinationId: String): String {
        if (Strings.isNullOrEmpty(message)) {
            throw IllegalStateException("Message content missing in the Destination with id: ${destinationId}")
        }
        val messageContent: String? = if(Strings.isNullOrEmpty(subject)) message else subject + message
        val builder = XContentFactory.contentBuilder(XContentType.JSON)
        builder.startObject()
                .field("Content", messageContent)
                .endObject()
        return builder.string()
    }
}
