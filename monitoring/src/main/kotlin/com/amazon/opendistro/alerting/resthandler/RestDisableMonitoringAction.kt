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

package com.amazon.opendistro.alerting.resthandler

import com.amazon.opendistro.alerting.MonitoringPlugin
import com.amazon.opendistro.alerting.settings.MonitoringSettings
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.BytesRestResponse
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.rest.RestController
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestResponse
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.rest.action.RestResponseListener
import java.io.IOException
import java.lang.IllegalArgumentException

/**
 * This class consists of the REST handler to enable or disable the pluging.
 */
class RestDisableMonitoringAction(settings: Settings, controller: RestController) : BaseRestHandler(settings) {

    init {
        // Acknowledge alerts
        controller.registerHandler(RestRequest.Method.POST, "${MonitoringPlugin.MONITOR_BASE_URI}/settings", this)
    }

    override fun getName(): String {
        return "disable_monitoring_action"
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        var updateRequest = ClusterUpdateSettingsRequest().persistentSettings(parseSetting(request.contentOrSourceParamParser()))
        return RestChannelConsumer {  channel -> client.admin().cluster().updateSettings(updateRequest, updateSettingsResponse(channel)) }
    }

    private fun updateSettingsResponse(channel: RestChannel): RestResponseListener<ClusterUpdateSettingsResponse> {
        return object: RestResponseListener<ClusterUpdateSettingsResponse>(channel) {
            @Throws(Exception::class)
            override fun buildResponse(response: ClusterUpdateSettingsResponse): RestResponse {
                if (!response.isAcknowledged) {
                    return BytesRestResponse(RestStatus.BAD_REQUEST, channel.newBuilder().startObject().field("acknowledged", false).endObject())
                }
                var builder = channel.newBuilder()
                        .startObject()
                        .field("acknowledged", true)
                        .startObject("persistent")
                        .value(response.persistentSettings)
                        .endObject()
                        .startObject("transient")
                        .value(response.transientSettings)
                        .endObject()
                        .endObject()
                return BytesRestResponse(RestStatus.OK, builder)
            }
        }
    }

    private fun parseSetting(xcp: XContentParser): Settings {
        val enabledKeySetting = MonitoringSettings.MONITORING_ENABLED.key
        val map = xcp.map()
        if (!map.containsKey(enabledKeySetting)) {
            throw IllegalArgumentException("Expected setting: $enabledKeySetting")
        }
        val value = try {
            map[enabledKeySetting] as Boolean
        } catch (e: Exception) {
            throw IllegalArgumentException("Expecting $enabledKeySetting to be boolean")
        }
        return Settings.builder().put(enabledKeySetting, value).build()
    }
}
