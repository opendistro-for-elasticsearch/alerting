package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.test.ESTestCase
import org.junit.Assert

class DeleteDestinationRequestTests : ESTestCase() {

    fun `test delete destination request`() {

        val req = DeleteDestinationRequest("1234", WriteRequest.RefreshPolicy.IMMEDIATE)
        Assert.assertNotNull(req)
        Assert.assertEquals("1234", req.destinationId)
        Assert.assertEquals("true", req.refreshPolicy?.value)

        val out = BytesStreamOutput()
        req.writeTo(out)
        val sin = StreamInput.wrap(out.bytes().toBytesRef().bytes)
        val newReq = DeleteDestinationRequest(sin)
        Assert.assertEquals("1234", newReq.destinationId)
        Assert.assertEquals("true", newReq.refreshPolicy?.value)
    }
}
