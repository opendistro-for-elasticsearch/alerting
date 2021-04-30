/*
 *   Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.model

import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.ACTIONS_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.ID_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.NAME_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.Trigger.Companion.SEVERITY_FIELD
import com.amazon.opendistroforelasticsearch.alerting.model.action.Action
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.UUIDs
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * A multi-alert Trigger available with Aggregation Monitors that filters aggregation buckets via a pipeline
 * aggregator.
 */
data class AggregationTrigger(
    override val id: String = UUIDs.base64UUID(),
    override val name: String,
    override val severity: String,
    override val actions: List<Action>
    // TODO: Add the remaining member variables based on integrated with Trigger pipeline aggregator
) : Trigger {

    // TODO: Once class is full implemented add tests to the following suites:
    //  - WriteableTests
    //  - XContentTests

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readString(), // id
        sin.readString(), // name
        sin.readString(), // severity
        sin.readList(::Action) // actions
        // TODO: Add remainder of inputs after integration
    )

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
            .startObject(AGGREGATION_TRIGGER_FIELD)
            .field(ID_FIELD, id)
            .field(NAME_FIELD, name)
            .field(SEVERITY_FIELD, severity)
            .field(ACTIONS_FIELD, actions.toTypedArray())
            // TODO: Add remainder of contents after integration
            .endObject()
            .endObject()
        return builder
    }

    override fun name(): String {
        return AGGREGATION_TRIGGER_FIELD
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeString(name)
        out.writeString(severity)
        out.writeCollection(actions)
        // TODO: Add remainder of outputs after integration
    }

    companion object {
        const val AGGREGATION_TRIGGER_FIELD = "aggregation_trigger"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Trigger::class.java, ParseField(AGGREGATION_TRIGGER_FIELD),
            CheckedFunction { parseInner(it) })

        @JvmStatic
        @Throws(IOException::class)
        fun parseInner(xcp: XContentParser): AggregationTrigger {
            var id = UUIDs.base64UUID() // assign a default triggerId if one is not specified
            lateinit var name: String
            lateinit var severity: String
            val actions: MutableList<Action> = mutableListOf()
            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp)

            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()

                xcp.nextToken()
                when (fieldName) {
                    ID_FIELD -> id = xcp.text()
                    NAME_FIELD -> name = xcp.text()
                    SEVERITY_FIELD -> severity = xcp.text()
                    ACTIONS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            actions.add(Action.parse(xcp))
                        }
                    }
                    // TODO: Add remainder of field to parse after integration
                }
            }

            return AggregationTrigger(
                id = requireNotNull(id) { "Trigger id is null." },
                name = requireNotNull(name) { "Trigger name is null" },
                severity = requireNotNull(severity) { "Trigger severity is null" },
                actions = requireNotNull(actions) { "Trigger actions are null" })
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): AggregationTrigger {
            return AggregationTrigger(sin)
        }
    }
}
