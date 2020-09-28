package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.builder
import com.amazon.opendistroforelasticsearch.alerting.elasticapi.string
import com.amazon.opendistroforelasticsearch.alerting.model.Alert
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.test.ESTestCase
import org.junit.Assert
import java.time.Instant
import java.util.Collections

class GetAlertsResponseTests : ESTestCase() {

    fun `test get alerts response with no alerts`() {
        val req = GetAlertsResponse(Collections.emptyList(), 0)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetAlertsResponse(sin)
        Assert.assertTrue(newReq.alerts.isEmpty())
        assertEquals(0, newReq.totalAlerts)
    }

    fun `test get alerts response with alerts`() {
        val alert = Alert(
                "id",
                0L,
                0,
                "monitorId",
                "monitorName",
                0L,
                "triggerId",
                "triggerName",
                Alert.State.ACKNOWLEDGED,
                Instant.MIN,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                "severity",
                Collections.emptyList()
        )
        val req = GetAlertsResponse(listOf(alert), 1)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetAlertsResponse(sin)
        assertEquals(1, newReq.alerts.size)
        assertEquals(alert, newReq.alerts[0])
        assertEquals(1, newReq.totalAlerts)
    }

    fun `test toXContent for get alerts response`() {
        val now = Instant.now()

        val alert = Alert(
                "id",
                0L,
                0,
                "monitorId",
                "monitorName",
                0L,
                "triggerId",
                "triggerName",
                Alert.State.ACKNOWLEDGED,
                now,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                "severity",
                Collections.emptyList()
        )
        val req = GetAlertsResponse(listOf(alert), 1)
        var actualXContentString = req.toXContent(builder(), ToXContent.EMPTY_PARAMS).string()
        val expectedXContentString = "{\"alerts\":[{\"alert_id\":\"id\",\"alert_version\":0,\"monitor_id\":\"monitorId\"," +
                "\"schema_version\":0,\"monitor_version\":0,\"monitor_name\":\"monitorName\",\"trigger_id\":\"triggerId\"," +
                "\"trigger_name\":\"triggerName\",\"state\":\"ACKNOWLEDGED\",\"error_message\":null,\"alert_history\":[]," +
                "\"severity\":\"severity\",\"action_execution_results\":[],\"start_time\":" + now.toEpochMilli() +
                ",\"last_notification_time\":null,\"end_time\":null,\"acknowledged_time\":null}],\"totalAlerts\":1}"
        assertEquals(expectedXContentString, actualXContentString)
    }
//
//    fun `test get monitor response with monitor`() {
//        val cronExpression = "31 * * * *" // Run at minute 31.
//        val testInstance = Instant.ofEpochSecond(1538164858L)
//
//        val cronSchedule = CronSchedule(cronExpression, ZoneId.of("Asia/Kolkata"), testInstance)
//        val req = GetMonitorResponse("1234", 1L, 2L, 0L, RestStatus.OK,
//                Monitor("123", 0L, "test-monitor", true, cronSchedule, Instant.now(),
//                        Instant.now(), randomUser(), 0, mutableListOf(), mutableListOf(), mutableMapOf()))
//        assertNotNull(req)
//
//        val out = BytesStreamOutput()
//        req.writeTo(out)
//        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
//        val newReq = GetMonitorResponse(sin)
//        assertEquals("1234", newReq.id)
//        assertEquals(1L, newReq.version)
//        assertEquals(RestStatus.OK, newReq.status)
//        assertNotNull(newReq.monitor)
//    }
}
