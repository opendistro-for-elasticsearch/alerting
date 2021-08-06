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

package com.amazon.opendistroforelasticsearch.alerting.model.action

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * This class represents the container for various configurations which control Action behavior.
 */
// TODO: Should throttleEnabled be included in here as well?
data class ActionExecutionPolicy(
    val throttle: Throttle? = null,
    val actionExecutionScope: ActionExecutionScope
) : Writeable, ToXContentObject {

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this (
        sin.readOptionalWriteable(::Throttle), // throttle
        ActionExecutionScope.readFrom(sin) // actionExecutionFrequency
    )

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        val xContentBuilder = builder.startObject()
        if (throttle != null) {
            xContentBuilder.field(THROTTLE_FIELD, throttle)
        }
        xContentBuilder.field(ACTION_EXECUTION_SCOPE, actionExecutionScope)
        return xContentBuilder.endObject()
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        if (throttle != null) {
            out.writeBoolean(true)
            throttle.writeTo(out)
        } else {
            out.writeBoolean(false)
        }
        if (actionExecutionScope is PerAlertActionScope) {
            out.writeEnum(ActionExecutionScope.Type.PER_ALERT)
        } else {
            out.writeEnum(ActionExecutionScope.Type.PER_EXECUTION)
        }
        actionExecutionScope.writeTo(out)
    }

    companion object {
        const val THROTTLE_FIELD = "throttle"
        const val ACTION_EXECUTION_SCOPE = "action_execution_scope"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): ActionExecutionPolicy {
            var throttle: Throttle? = null
            lateinit var actionExecutionScope: ActionExecutionScope

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                when (fieldName) {
                    THROTTLE_FIELD -> {
                        throttle = if (xcp.currentToken() == Token.VALUE_NULL) null else Throttle.parse(xcp)
                    }
                    ACTION_EXECUTION_SCOPE -> actionExecutionScope = ActionExecutionScope.parse(xcp)
                }
            }

            return ActionExecutionPolicy(
                throttle,
                requireNotNull(actionExecutionScope) { "Action execution scope is null" }
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): ActionExecutionPolicy {
            return ActionExecutionPolicy(sin)
        }

        /**
         * The default [ActionExecutionPolicy] configuration.
         *
         * This is currently only used by Bucket-Level Monitors and was configured with that in mind.
         * If Query-Level Monitors integrate the use of [ActionExecutionPolicy] then a separate default configuration
         * might need to be made depending on the desired behavior.
         */
        fun getDefaultConfiguration(): ActionExecutionPolicy {
            val defaultActionExecutionFrequency = PerAlertActionScope(
                actionableAlerts = setOf(AlertCategory.DEDUPED, AlertCategory.NEW)
            )
            return ActionExecutionPolicy(throttle = null, actionExecutionScope = defaultActionExecutionFrequency)
        }
    }
}
