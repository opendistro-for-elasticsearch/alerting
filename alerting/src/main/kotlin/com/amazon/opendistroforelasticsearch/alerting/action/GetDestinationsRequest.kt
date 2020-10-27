/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.elasticsearch.action.ActionRequest
import org.elasticsearch.action.ActionRequestValidationException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import java.io.IOException

class GetDestinationsRequest : ActionRequest {
    val destinationId: String?
    val version: Long
    val srcContext: FetchSourceContext?
    val table: Table
    val destinationType: String

    constructor(
        destinationId: String?,
        version: Long,
        srcContext: FetchSourceContext?,
        table: Table,
        destinationType: String
    ) : super() {
        this.destinationId = destinationId
        this.version = version
        this.srcContext = srcContext
        this.table = table
        this.destinationType = destinationType
    }

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        destinationId = sin.readOptionalString(),
        version = sin.readLong(),
        srcContext = if (sin.readBoolean()) {
            FetchSourceContext(sin)
        } else null,
        table = Table.readFrom(sin),
        destinationType = sin.readString()
    )

    override fun validate(): ActionRequestValidationException? {
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeOptionalString(destinationId)
        out.writeLong(version)
        out.writeBoolean(srcContext != null)
        srcContext?.writeTo(out)
        table.writeTo(out)
        out.writeString(destinationType)
    }
}
