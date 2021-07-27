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
import java.lang.IllegalArgumentException

/**
 * This class represents configurations used to control the scope of Action executions when Alerts are created.
 */
sealed class ActionExecutionScope : Writeable, ToXContentObject {

    enum class Type { PER_ALERT, PER_EXECUTION }

    companion object {
        const val PER_ALERT_FIELD = "per_alert"
        const val PER_EXECUTION_FIELD = "per_execution"
        const val ACTIONABLE_ALERTS_FIELD = "actionable_alerts"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): ActionExecutionScope {
            var type: Type? = null
            var actionExecutionScope: ActionExecutionScope? = null
            val alertFilter = mutableSetOf<AlertCategory>()

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                // If the type field has already been set, the user has provided more than one type of schedule
                if (type != null) {
                    throw IllegalArgumentException("You can only specify one type of action execution scope.")
                }

                when (fieldName) {
                    PER_ALERT_FIELD -> {
                        type = Type.PER_ALERT
                        while (xcp.nextToken() != Token.END_OBJECT) {
                            val perAlertFieldName = xcp.currentName()
                            xcp.nextToken()
                            when (perAlertFieldName) {
                                ACTIONABLE_ALERTS_FIELD -> {
                                    ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp)
                                    val allowedCategories = AlertCategory.values().map { it.toString() }
                                    while (xcp.nextToken() != Token.END_ARRAY) {
                                        val alertCategory = xcp.text()
                                        if (!allowedCategories.contains(alertCategory)) {
                                            throw IllegalStateException("Actionable alerts should be one of $allowedCategories")
                                        }
                                        alertFilter.add(AlertCategory.valueOf(alertCategory))
                                    }
                                }
                            }
                        }
                    }
                    PER_EXECUTION_FIELD -> {
                        type = Type.PER_EXECUTION
                        while (xcp.nextToken() != Token.END_OBJECT) {}
                    }
                    else -> throw IllegalArgumentException("Invalid field [$fieldName] found in action execution scope.")
                }
            }

            if (type == Type.PER_ALERT) {
                actionExecutionScope = PerAlertActionScope(alertFilter)
            } else if (type == Type.PER_EXECUTION) {
                actionExecutionScope = PerExecutionActionScope()
            }

            return requireNotNull(actionExecutionScope) { "Action execution scope is null." }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): ActionExecutionScope {
            val type = sin.readEnum(ActionExecutionScope.Type::class.java)
            return if (type == Type.PER_ALERT) {
                PerAlertActionScope(sin)
            } else {
                PerExecutionActionScope(sin)
            }
        }
    }

    abstract fun getExecutionFrequency(): Type
}

data class PerAlertActionScope(
    val actionableAlerts: Set<AlertCategory>
) : ActionExecutionScope() {

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readSet { si -> si.readEnum(AlertCategory::class.java) } // alertFilter
    )

    override fun getExecutionFrequency(): Type = Type.PER_ALERT

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
            .startObject(PER_ALERT_FIELD)
            .field(ACTIONABLE_ALERTS_FIELD, actionableAlerts.toTypedArray())
            .endObject()
        return builder.endObject()
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeCollection(actionableAlerts) { o, v -> o.writeEnum(v) }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): PerAlertActionScope {
            return PerAlertActionScope(sin)
        }
    }
}

class PerExecutionActionScope() : ActionExecutionScope() {

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this()

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    // Creating an equals method that just checks class type rather than reference since this is currently stateless.
    // Otherwise, it would have been a dataclass which would have handled this.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        return true
    }

    override fun getExecutionFrequency(): Type = Type.PER_EXECUTION

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
            .startObject(PER_EXECUTION_FIELD)
            .endObject()
        return builder.endObject()
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {}

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): PerExecutionActionScope {
            return PerExecutionActionScope(sin)
        }
    }
}

enum class AlertCategory { DEDUPED, NEW, COMPLETED }
