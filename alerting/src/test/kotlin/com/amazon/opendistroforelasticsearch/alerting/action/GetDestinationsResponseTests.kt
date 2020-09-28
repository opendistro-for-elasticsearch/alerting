package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Slack
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase
import java.time.Instant
import java.util.Collections

class GetDestinationsResponseTests : ESTestCase() {

    fun `test get destination response with no destinations`() {
        val req = GetDestinationsResponse(RestStatus.OK, 0, Collections.emptyList())
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsResponse(sin)
        assertEquals(0, newReq.totalDestinations)
        assertTrue(newReq.destinations.isEmpty())
        assertEquals(RestStatus.OK, newReq.status)
    }

    fun `test get destination response with a destination`() {
//        val id: String = NO_ID,
//        val version: Long = NO_VERSION,
//        val schemaVersion: Int = NO_SCHEMA_VERSION,
//        val seqNo: Int = NO_SEQ_NO,
//        val primaryTerm: Int = NO_PRIMARY_TERM,
//        val type: DestinationType,
//        val name: String,
//        val user: User?,
//        val lastUpdateTime: Instant,
//        val chime: Chime?,
//        val slack: Slack?,
//        val customWebhook: CustomWebhook?
        val slack = Slack("url")
        val destination = Destination(
                "id",
                0L,
                0,
                0,
                0,
                DestinationType.SLACK,
                "name",
                null,
                Instant.MIN,
                null,
                slack,
                null)
//        val req = GetMonitorResponse("1234", 1L, 2L, 0L, RestStatus.OK,
//                Monitor("123", 0L, "test-monitor", true, cronSchedule, Instant.now(),
//                        Instant.now(), randomUser(), 0, mutableListOf(), mutableListOf(), mutableMapOf()))
        val req = GetDestinationsResponse(RestStatus.OK, 1, listOf(destination))
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsResponse(sin)
        assertEquals(1, newReq.totalDestinations)
        assertEquals(destination, newReq.destinations[0])
        assertEquals(RestStatus.OK, newReq.status)
    }
}
