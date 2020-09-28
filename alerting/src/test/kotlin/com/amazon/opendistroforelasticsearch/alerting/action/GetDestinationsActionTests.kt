package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.test.ESTestCase

class GetDestinationsActionTests : ESTestCase() {

    fun `test get destinations action name`() {
        assertNotNull(GetDestinationsAction.INSTANCE.name())
        assertEquals(GetDestinationsAction.INSTANCE.name(), GetDestinationsAction.NAME)
    }
}
