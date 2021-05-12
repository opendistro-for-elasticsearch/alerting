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

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertError
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.script.ScriptException
import java.io.IOException
import java.time.Instant

data class TraditionalTriggerRunResult (
    override var triggerName: String,
    var triggered: Boolean,
    override var error: Exception?,
    var actionResults: MutableMap<String, ActionRunResult> = mutableMapOf()
) : TriggerRunResult (triggerName, error) {


    @Throws(IOException::class)
    @Suppress("UNCHECKED_CAST")
    constructor(sin: StreamInput) : this(
        triggerName=sin.readString(),
        error=sin.readException(),
        triggered = sin.readBoolean(),
        actionResults = sin.readMap() as MutableMap<String, ActionRunResult>
    )

    override fun alertError(): AlertError? {
        if (error != null) {
            return AlertError(Instant.now(), "Failed evaluating trigger:\n${error!!.userErrorMessage()}")
        }
        for (actionResult in actionResults.values) {
            if (actionResult.error != null) {
                return AlertError(Instant.now(), "Failed running action:\n${actionResult.error.userErrorMessage()}")
            }
        }
        return null
    }

    override fun internalXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        if (error is ScriptException) error = Exception((error as ScriptException).toJsonString(), error)
        return builder
            .field("triggered", triggered)
            .field("action_results", actionResults as Map<String, ActionRunResult>)
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        super.writeTo(out)
        out.writeBoolean(triggered)
        out.writeMap(actionResults as Map<String, ActionRunResult>)
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): TriggerRunResult {
            return TraditionalTriggerRunResult(sin)
        }
    }
}
