/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring

import org.elasticsearch.common.SuppressForbidden
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType

@SuppressForbidden(reason = "cos")
class MonitorRunnerTests : MonitoringRestTestCase() {

//     fun `test execute monitor`() {
//        val monitor = randomMonitor()
//        val response = client().performRequest("POST", "/_awses/monitors/_execute", emptyMap(), monitor.toHttpEntity())
//
//         val xcp = createParser(XContentType.JSON.xContent(), response.entity.content)
//         val output = xcp.map()
//         assertEquals(monitor.name, output["monitor_name"])
//    }

    // Useful settings when debugging to prevent timeouts
    override fun restClientSettings(): Settings {
        return Settings.builder()
//                .put(CLIENT_RETRY_TIMEOUT, TimeValue.timeValueMinutes(10))
//                .put(CLIENT_SOCKET_TIMEOUT, TimeValue.timeValueMinutes(10))
                .build()
    }
}
