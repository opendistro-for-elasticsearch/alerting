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

import org.elasticsearch.common.ParsingException
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.util.Locale

data class AggregationResultBucket(
    val parentBucketPath: String?,
    val bucketKeys: List<String>,
    val bucket: Map<String, Any>?
) : Writeable, ToXContent {

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(sin.readString(), sin.readStringList(), sin.readMap())

    override fun writeTo(out: StreamOutput) {
        out.writeString(parentBucketPath)
        out.writeStringCollection(bucketKeys)
        out.writeMap(bucket)
    }

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject(CONFIG_NAME)
            .field(PARENTS_BUCKET_PATH, parentBucketPath)
            .field(BUCKET_KEYS, bucketKeys.toTypedArray())
            .field(BUCKET, bucket)
            .endObject()
        return builder
    }

    companion object {
        const val CONFIG_NAME = "agg_alert_content"
        const val PARENTS_BUCKET_PATH = "parent_bucket_path"
        const val BUCKET_KEYS = "bucket_key"
        private const val BUCKET = "bucket"

        fun parse(xcp: XContentParser): AggregationResultBucket {
            var parentBucketPath: String? = null
            var bucketKeys = mutableListOf<String>()
            var bucket: MutableMap<String, Any>? = null
            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp)

            if (CONFIG_NAME != xcp.currentName()) {
                throw ParsingException(xcp.tokenLocation,
                    String.format(
                        Locale.ROOT, "Failed to parse object: expecting token with name [%s] but found [%s]",
                        CONFIG_NAME, xcp.currentName())
                )
            }
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    PARENTS_BUCKET_PATH -> parentBucketPath = xcp.text()
                    BUCKET_KEYS -> {
                        ensureExpectedToken(Token.START_ARRAY, xcp.currentToken(), xcp)
                        while (xcp.nextToken() != Token.END_ARRAY) {
                            bucketKeys.add(xcp.text())
                        }
                    }
                    BUCKET -> bucket = xcp.map()
                }
            }
            return AggregationResultBucket(parentBucketPath, bucketKeys, bucket)
        }
    }
}
