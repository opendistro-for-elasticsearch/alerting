package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.test.ESTestCase

class GetAlertsRequestTests : ESTestCase() {

    fun `test get alerts request`() {

        val table = Table("asc", "sortString", null, 1, 0, "")

        val req = GetAlertsRequest(table, "1", "active", null)
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetAlertsRequest(sin)

        assertEquals("1", newReq.severityLevel)
        assertEquals("active", newReq.alertState)
        assertNull(newReq.monitorId)
        assertEquals(table, newReq.table)
    }

    fun `test validate returns null`() {
        val table = Table("asc", "sortString", null, 1, 0, "")

        val req = GetAlertsRequest(table, "1", "active", null)
        assertNotNull(req)
        assertNull(req.validate())
    }

//    fun `test get monitor request without src context`() {
//
//        val req = GetMonitorRequest("1234", 1L, RestRequest.Method.GET, null)
//        assertNotNull(req)
//
//        val out = BytesStreamOutput()
//        req.writeTo(out)
//        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
//        val newReq = GetMonitorRequest(sin)
//        assertEquals("1234", newReq.monitorId)
//        assertEquals(1L, newReq.version)
//        assertEquals(RestRequest.Method.GET, newReq.method)
//        assertEquals(null, newReq.srcContext)
//    }
//
//    fun `test head monitor request`() {
//
//        val req = GetMonitorRequest("1234", 2L, RestRequest.Method.HEAD, FetchSourceContext.FETCH_SOURCE)
//        assertNotNull(req)
//
//        val out = BytesStreamOutput()
//        req.writeTo(out)
//        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
//        val newReq = GetMonitorRequest(sin)
//        assertEquals("1234", newReq.monitorId)
//        assertEquals(2L, newReq.version)
//        assertEquals(RestRequest.Method.HEAD, newReq.method)
//        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
//    }
}
