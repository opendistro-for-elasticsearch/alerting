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

package com.amazon.opendistroforelasticsearch.alerting.model

import com.amazon.opendistroforelasticsearch.alerting.core.model.Input
import org.elasticsearch.common.CheckedFunction
import org.elasticsearch.common.ParseField
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

/**
 * This class represents anomaly detector which will be used by monitor.
 * The anomaly detector's result will be used as monitor's input result.
 */
data class AnomalyDetectorInput(val detectorId: String) : Input {

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.startObject()
                .startObject(ANOMALY_DETECTOR_FIELD)
                .field(DETECTOR_ID_FIELD, detectorId)
                .endObject()
                .endObject()
    }

    override fun name(): String {
        return ANOMALY_DETECTOR_FIELD
    }

    companion object {
        const val ANOMALY_DETECTOR_FIELD = "anomaly_detector"
        const val DETECTOR_ID_FIELD = "detector_id"

        val XCONTENT_REGISTRY = NamedXContentRegistry.Entry(Input::class.java, ParseField(ANOMALY_DETECTOR_FIELD),
                CheckedFunction { parseInner(it) })

        @JvmStatic @Throws(IOException::class)
        private fun parseInner(xcp: XContentParser): AnomalyDetectorInput {
            lateinit var detectorId: String

            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            while (xcp.nextToken() != Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    DETECTOR_ID_FIELD -> {
                        detectorId = xcp.text()
                    }
                }
            }

            return AnomalyDetectorInput(detectorId)
        }
    }
}
