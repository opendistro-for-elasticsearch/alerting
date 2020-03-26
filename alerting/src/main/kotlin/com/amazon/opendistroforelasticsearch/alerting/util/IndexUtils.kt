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

package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.IndicesAdminClient
import org.elasticsearch.cluster.ClusterState
import org.elasticsearch.cluster.metadata.IndexMetaData
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentType

class IndexUtils {

    companion object {
        const val _META = "_meta"
        const val SCHEMA_VERSION = "schema_version"
        const val NO_SCHEMA_VERSION = 0

        var scheduledJobIndexSchemaVersion: Int
            private set
        var alertIndexSchemaVersion: Int
            private set

        var scheduledJobIndexUpdated: Boolean = false
            private set
        var alertIndexUpdated: Boolean = false
            private set
        var lastUpdatedHistoryIndex: String? = null

        init {
            scheduledJobIndexSchemaVersion = getSchemaVersion(ScheduledJobIndices.scheduledJobMappings())
            alertIndexSchemaVersion = getSchemaVersion(AlertIndices.alertMapping())
        }

        @JvmStatic
        fun scheduledJobIndexUpdated() {
            scheduledJobIndexUpdated = true
        }

        @JvmStatic
        fun alertIndexUpdated() {
            alertIndexUpdated = true
        }

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
                            when (xcp.currentName()) {
                                SCHEMA_VERSION -> {
                                    val version = xcp.intValue()
                                    require(version > -1)
                                    return version
                                }
                                else -> xcp.nextToken()
                            }
                        }
                    }
                }
                xcp.nextToken()
            }
            return NO_SCHEMA_VERSION
        }

        @JvmStatic
        fun getIndexNameWithAlias(clusterState: ClusterState, alias: String): String {
            return clusterState.metaData.indices.first { it.value.aliases.containsKey(alias) }.key
        }

        @JvmStatic
        fun shouldUpdateIndex(index: IndexMetaData, mapping: String): Boolean {
            var oldVersion = NO_SCHEMA_VERSION
            val newVersion = getSchemaVersion(mapping)

            val indexMapping = index.mapping()?.sourceAsMap()
            if (indexMapping != null && indexMapping.containsKey(_META) && indexMapping[_META] is HashMap<*, *>) {
                val metaData = indexMapping[_META] as HashMap<*, *>
                if (metaData.containsKey(SCHEMA_VERSION)) {
                    oldVersion = metaData[SCHEMA_VERSION] as Int
                }
            }
            return newVersion > oldVersion
        }

        @JvmStatic
        fun updateIndexMapping(
            index: String,
            type: String,
            mapping: String,
            clusterState: ClusterState,
            client: IndicesAdminClient,
            actionListener: ActionListener<AcknowledgedResponse>
        ) {
            if (clusterState.metaData.indices.containsKey(index)) {
                if (shouldUpdateIndex(clusterState.metaData.indices[index], mapping)) {
                    val putMappingRequest: PutMappingRequest = PutMappingRequest(index).type(type).source(mapping, XContentType.JSON)
                    client.putMapping(putMappingRequest, actionListener)
                } else {
                    actionListener.onResponse(AcknowledgedResponse(true))
                }
            }
        }
    }
}
