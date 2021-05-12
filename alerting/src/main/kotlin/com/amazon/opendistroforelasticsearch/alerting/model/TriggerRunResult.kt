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
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import java.io.IOException
import java.time.Instant

abstract class TriggerRunResult(
    open var triggerName: String,
    open var error: Exception? = null
) : Writeable, ToXContent {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        val msg = error?.message
        builder.startObject()
            .field("name", triggerName)
            .field("error", msg)
        internalXContent(builder, params)
        builder.endObject()
        return builder
    }

    abstract fun internalXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder

    /** Returns error information to store in the Alert. Currently it's just the stack trace but it can be more */
    open fun alertError(): AlertError? {
        if (error != null) {
            return AlertError(Instant.now(), "Failed evaluating trigger:\n${error!!.userErrorMessage()}")
        }
        return null
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(triggerName)
        out.writeException(error)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun suppressWarning(map: MutableMap<String?, Any?>?): MutableMap<String, ActionRunResult> {
            return map as MutableMap<String, ActionRunResult>
        }
    }
}
