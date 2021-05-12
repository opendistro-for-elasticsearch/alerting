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

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import java.io.IOException

data class AggregationTriggerRunResult(
    override var triggerName: String,
    override var error: Exception? = null,
    var aggregationResultBuckets: Map<String?, AggregationResultBucket>,
    var actionResultsMap: MutableMap<String?, MutableMap<String, ActionRunResult>> = mutableMapOf()
) : TriggerRunResult(triggerName, error) {

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        sin.readString(),
        sin.readException() as Exception?, // error
        sin.readMap(StreamInput::readString, ::AggregationResultBucket),
        sin.readMap(StreamInput::readString) { innerSin: StreamInput -> innerSin.readMap(StreamInput::readString, ::ActionRunResult) }
    )

    override fun internalXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder
            .field(AGG_RESULT_BUCKETS, aggregationResultBuckets)
            .field(ACTIONS_RESULTS, actionResultsMap as Map<String?, Any>?)
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        super.writeTo(out)
        out.writeMap<String?, AggregationResultBucket>(aggregationResultBuckets, StreamOutput::writeString) {
                valueOut: StreamOutput, aggResultBucket: AggregationResultBucket -> aggResultBucket.writeTo(valueOut)
        }

        out.writeMap<String?, MutableMap<String, ActionRunResult>>(actionResultsMap, StreamOutput::writeString) {
                actionOut: StreamOutput, value: MutableMap<String, ActionRunResult> -> actionOut.writeMap<String, ActionRunResult>(
            value, StreamOutput::writeString) {
                    actionResultOut: StreamOutput, actionResult: ActionRunResult -> actionResult.writeTo(actionResultOut)
            }
        }
    }

    companion object {
        const val AGG_RESULT_BUCKETS = "agg_result_buckets"
        const val ACTIONS_RESULTS = "action_results"

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): TriggerRunResult {
            return AggregationTriggerRunResult(sin)
        }
    }
}
