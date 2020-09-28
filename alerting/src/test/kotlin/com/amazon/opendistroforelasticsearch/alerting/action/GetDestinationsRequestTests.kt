package com.amazon.opendistroforelasticsearch.alerting.action

import com.amazon.opendistroforelasticsearch.alerting.model.Table
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import org.elasticsearch.test.ESTestCase

class GetDestinationsRequestTests : ESTestCase() {

    fun `test get destination request`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetDestinationsRequest("1234", 1L, FetchSourceContext.FETCH_SOURCE, table, "slack")
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsRequest(sin)
        assertEquals("1234", newReq.destinationId)
        assertEquals(1L, newReq.version)
        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
        assertEquals(table, newReq.table)
        assertEquals("slack", newReq.destinationType)
    }

    fun `test get destination request without src context`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetDestinationsRequest("1234", 1L, null, table, "slack")
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsRequest(sin)
        assertEquals("1234", newReq.destinationId)
        assertEquals(1L, newReq.version)
        assertEquals(null, newReq.srcContext)
        assertEquals(table, newReq.table)
        assertEquals("slack", newReq.destinationType)
    }

    fun `test get destination request without destinationId`() {

        val table = Table("asc", "sortString", null, 1, 0, "")
        val req = GetDestinationsRequest(null, 1L, FetchSourceContext.FETCH_SOURCE, table, "slack")
        assertNotNull(req)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = GetDestinationsRequest(sin)
        assertEquals(null, newReq.destinationId)
        assertEquals(1L, newReq.version)
        assertEquals(FetchSourceContext.FETCH_SOURCE, newReq.srcContext)
        assertEquals(table, newReq.table)
        assertEquals("slack", newReq.destinationType)
    }
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
