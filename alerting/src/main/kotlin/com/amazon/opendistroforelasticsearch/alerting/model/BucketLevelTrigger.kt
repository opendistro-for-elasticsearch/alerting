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

import com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext.BucketSelectorExtAggregationBuilder
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
 * A multi-alert Trigger available with Bucket-Level Monitors that filters aggregation buckets via a pipeline
 * aggregator.
 */
data class BucketLevelTrigger(
    override val id: String = UUIDs.base64UUID(),
    override val name: String,
    override val severity: String,
    val bucketSelector: BucketSelectorExtAggregationBuilder,
    override val actions: List<Action>
) : Trigger {

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readString(), // id
        sin.readString(), // name
        sin.readString(), // severity
        BucketSelectorExtAggregationBuilder(sin), // condition
        sin.readList(::Action) // actions
    )

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
            .startObject(BUCKET_LEVEL_TRIGGER_FIELD)
            .field(ID_FIELD, id)
            .field(NAME_FIELD, name)
            .field(SEVERITY_FIELD, severity)
            .startObject(CONDITION_FIELD)
        bucketSelector.internalXContent(builder, params)
        builder.endObject()
            .field(ACTIONS_FIELD, actions.toTypedArray())
            .endObject()
            .endObject()
        return builder
    }

    override fun name(): String {
        return BUCKET_LEVEL_TRIGGER_FIELD
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(id)
        out.writeString(name)
        out.writeString(severity)
        bucketSelector.writeTo(out)
        out.writeCollection(actions)
    }

    fun asTemplateArg(): Map<String, Any> {
        return mapOf(
            ID_FIELD to id,
            NAME_FIELD to name,
            SEVERITY_FIELD to severity,
            ACTIONS_FIELD to actions.map { it.asTemplateArg() },
            PARENT_BUCKET_PATH to getParentBucketPath()
        )
    }

    fun getParentBucketPath(): String {
        return bucketSelector.parentBucketPath
    }

    companion object {
        const val BUCKET_LEVEL_TRIGGER_FIELD = "bucket_level_trigger"
        const val CONDITION_FIELD = "condition"
        const val PARENT_BUCKET_PATH = "parentBucketPath"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Trigger::class.java, ParseField(BUCKET_LEVEL_TRIGGER_FIELD),
            CheckedFunction { parseInner(it) })

        @JvmStatic
        @Throws(IOException::class)
        fun parseInner(xcp: XContentParser): BucketLevelTrigger {
            var id = UUIDs.base64UUID() // assign a default triggerId if one is not specified
            lateinit var name: String
            lateinit var severity: String
            val actions: MutableList<Action> = mutableListOf()
            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp)
            lateinit var bucketSelector: BucketSelectorExtAggregationBuilder

            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()

                xcp.nextToken()
                when (fieldName) {
                    ID_FIELD -> id = xcp.text()
                    NAME_FIELD -> name = xcp.text()
                    SEVERITY_FIELD -> severity = xcp.text()
                    CONDITION_FIELD -> {
                        // Using the trigger id as the name in the bucket selector since it is validated for uniqueness within Monitors
                        // and the id is not given by users when making API requests so it should be set before this is called.
                        // On the other hand, trigger name could potentially be given in any order in the JSON request and may not precede
                        // the condition field.
                        bucketSelector = BucketSelectorExtAggregationBuilder.parse(id, xcp)
                    }
                    ACTIONS_FIELD -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            actions.add(Action.parse(xcp))
                        }
                    }
                }
            }

            return BucketLevelTrigger(
                id = requireNotNull(id) { "Trigger id is null." },
                name = requireNotNull(name) { "Trigger name is null" },
                severity = requireNotNull(severity) { "Trigger severity is null" },
                bucketSelector = bucketSelector,
                actions = requireNotNull(actions) { "Trigger actions are null" })
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): BucketLevelTrigger {
            return BucketLevelTrigger(sin)
        }
    }
}
