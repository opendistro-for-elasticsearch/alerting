package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.ALERTING_BASE_URI
import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.makeRequest
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import com.amazon.opendistroforelasticsearch.alerting.randomAlert
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.junit.annotations.TestLogging

/**
 * client() - created with admin
 * adminClient() - created with kirk adminDn certs. (super-admin)
 */
@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class SecureMonitorRestApiIT : AlertingRestTestCase() {

    fun `test query a monitor with filter by roles`() {
        enableFilterBy()

        // creates monitor as "admin" user.
        val monitor = createRandomMonitor(true)
        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", monitor.id)).toString()

        // search as "admin" - must get 1 docs
        val adminSearchResponse = client().makeRequest("POST", "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, adminSearchResponse.restStatus())

        val adminHits = createParser(XContentType.JSON.xContent(),
                adminSearchResponse.entity.content).map()["hits"]!! as Map<String, Map<String, Any>>
        val adminDocsFound = adminHits["total"]?.get("value")
        assertEquals("Monitor not found during search", 1, adminDocsFound)

        // search as "kirk" - must get 0 docs
        val kirkSearchResponse = adminClient().makeRequest("POST", "$ALERTING_BASE_URI/_search",
                emptyMap(),
                NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search monitor failed", RestStatus.OK, kirkSearchResponse.restStatus())
        val kirkHits = createParser(XContentType.JSON.xContent(),
                kirkSearchResponse.entity.content).map()["hits"]!! as Map<String, Map<String, Any>>
        val kirkDocsFound = kirkHits["total"]?.get("value")

        val expected = when (isHttps()) {
            true -> 0
            false -> 1
        }
        assertEquals("Monitor not found during search", expected, kirkDocsFound)
    }

    fun `test get all alerts in all states with filter by roles`() {
        enableFilterBy()
        putAlertMappings()
        val monitor = createRandomMonitor(refresh = true)
        createAlert(randomAlert(monitor).copy(state = Alert.State.ACKNOWLEDGED))
        createAlert(randomAlert(monitor).copy(state = Alert.State.COMPLETED))
        createAlert(randomAlert(monitor).copy(state = Alert.State.ERROR))
        createAlert(randomAlert(monitor).copy(state = Alert.State.ACTIVE))
        randomAlert(monitor).copy(id = "foobar")

        val inputMap = HashMap<String, Any>()
        inputMap["missing"] = "_last"

        // search as "admin" - must get 0 docs
        val adminResponseMap = getAlerts(client(), inputMap).asMap()
        assertEquals(4, adminResponseMap["totalAlerts"])

        // search as "kirk" - must get 0 docs
        val expected = when (isHttps()) {
            true -> 0
            false -> 4
        }
        val kirkResponseMap = getAlerts(adminClient(), inputMap).asMap()
        assertEquals(expected, kirkResponseMap["totalAlerts"])
    }
}
