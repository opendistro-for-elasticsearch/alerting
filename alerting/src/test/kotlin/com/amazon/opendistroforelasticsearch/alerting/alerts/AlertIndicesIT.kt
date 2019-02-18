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

package com.amazon.opendistroforelasticsearch.alerting.alerts

import com.amazon.opendistroforelasticsearch.alerting.ALWAYS_RUN
import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.randomMonitor
import com.amazon.opendistroforelasticsearch.alerting.randomTrigger
import com.amazon.opendistroforelasticsearch.alerting.settings.AlertingSettings
import com.amazon.opendistroforelasticsearch.alerting.test.makeRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus

class AlertIndicesIT : AlertingRestTestCase() {

    fun `test create alert index`() {
        executeMonitor(randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN))))

        assertIndexExists(AlertIndices.ALERT_INDEX)
        assertIndexExists(AlertIndices.HISTORY_WRITE_INDEX)
    }

    fun `test alert index gets recreated automatically if deleted`() {
        val trueMonitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN)))

        executeMonitor(trueMonitor)
        assertIndexExists(AlertIndices.ALERT_INDEX)
        assertIndexExists(AlertIndices.HISTORY_WRITE_INDEX)

        client().makeRequest("DELETE", "*")
        assertIndexDoesNotExist(AlertIndices.ALERT_INDEX)
        assertIndexDoesNotExist(AlertIndices.HISTORY_WRITE_INDEX)

        val executeResponse = executeMonitor(trueMonitor)
        val xcp = createParser(XContentType.JSON.xContent(), executeResponse.entity.content)
        val output = xcp.map()
        assertNull("Error running a monitor after wiping alert indices", output["error"])
    }

    fun `test rollover history index`() {
        // Update the rollover check to be every 1 second and the index max age to be 1 second
        client().updateSettings(AlertingSettings.ALERT_HISTORY_ROLLOVER_PERIOD.key, "1s")
        client().updateSettings(AlertingSettings.ALERT_HISTORY_INDEX_MAX_AGE.key, "1s")

        val trueMonitor = randomMonitor(triggers = listOf(randomTrigger(condition = ALWAYS_RUN)))
        executeMonitor(trueMonitor)

        // Allow for a rollover index.
        Thread.sleep(2000)
        val response = client().makeRequest("GET", "/_cat/indices?format=json")
        val xcp = createParser(XContentType.JSON.xContent(), response.entity.content)
        val responseList = xcp.list()
        val indices = mutableListOf<String>()
        responseList.filterIsInstance<Map<String, Any>>().forEach { indices.add(it["index"] as String) }
        assertTrue("Did not find 3 alert indices", indices.size >= 3)
    }

    private fun assertIndexExists(index: String) {
        val response = client().makeRequest("HEAD", "$index")
        assertEquals("Index $index does not exist.", RestStatus.OK, response.restStatus())
    }

    private fun assertIndexDoesNotExist(index: String) {
        val response = client().makeRequest("HEAD", "$index")
        assertEquals("Index $index does not exist.", RestStatus.NOT_FOUND, response.restStatus())
    }
}
