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

package com.amazon.opendistroforelasticsearch.alerting.core.util

import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest
import org.elasticsearch.client.IndicesAdminClient
import org.elasticsearch.cluster.ClusterState
import org.elasticsearch.cluster.metadata.IndexMetaData
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType

class SchemaVersionUtils {

    companion object {
        const val _META = "_meta"
        const val SCHEMA_VERSION = "schema_version"

        private val logger = LogManager.getLogger(SchemaVersionUtils::class.java)

        @JvmStatic
        fun getSchemaVersion(mapping: String): Int {
            val xcp = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY,
                    LoggingDeprecationHandler.INSTANCE, mapping)

            while (!xcp.isClosed) {
                val token = xcp.currentToken()
                if (token != null && token != XContentParser.Token.END_OBJECT && token != XContentParser.Token.START_OBJECT) {
                    if (xcp.currentName() != _META) {
                        xcp.nextToken()
                        xcp.skipChildren()
                    } else {
                        while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                            if (xcp.currentName() == SCHEMA_VERSION) {
                                return xcp.intValue()
                            }
                            xcp.nextToken()
                        }
                    }
                }
                xcp.nextToken()
            }
            return 0
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun shouldUpdateIndex(index: IndexMetaData, mapping: String): Boolean {
            var oldVersion = 0
            val newVersion = getSchemaVersion(mapping)

            val indexMapping = index.mapping().sourceAsMap()
            if (indexMapping.containsKey(_META)) {
                val metaData = indexMapping[_META] as LinkedHashMap<String, Any>
                if (metaData.containsKey(SCHEMA_VERSION)) oldVersion = metaData[SCHEMA_VERSION] as Int
            }
            return newVersion > oldVersion
        }

        @JvmStatic
        fun updateIndexMapping(index: String, type: String, mapping: String, clusterState: ClusterState, client: IndicesAdminClient) {
            if (clusterState.metaData.indices.containsKey(index) &&
                    shouldUpdateIndex(clusterState.metaData.indices[index], mapping)) {
                var putMappingRequest: PutMappingRequest = PutMappingRequest(index).type(type).source(mapping, XContentType.JSON)
                client.preparePutMapping()
                client.putMapping(putMappingRequest)
                logger.info("Index mapping of $index is updated")
            }
        }
    }
}
