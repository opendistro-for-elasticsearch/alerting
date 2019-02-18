/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.core

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentFragment
import org.elasticsearch.common.xcontent.XContentBuilder

data class JobSweeperMetrics(val lastFullSweepTimeMillis: Long, val fullSweepOnTime: Boolean) : ToXContentFragment, Writeable {

    constructor(si: StreamInput) : this(si.readLong(), si.readBoolean())

    override fun writeTo(out: StreamOutput) {
        out.writeLong(lastFullSweepTimeMillis)
        out.writeBoolean(fullSweepOnTime)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.field("last_full_sweep_time_millis", lastFullSweepTimeMillis)
        builder.field("full_sweep_on_time", fullSweepOnTime)
        return builder
    }
}
