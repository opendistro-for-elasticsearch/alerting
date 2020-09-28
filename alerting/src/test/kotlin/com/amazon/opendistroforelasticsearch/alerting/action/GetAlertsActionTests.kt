package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.test.ESTestCase

class GetAlertsActionTests : ESTestCase() {

    fun `test get alerts action name`() {
        assertNotNull(GetAlertsAction.INSTANCE.name())
        assertEquals(GetAlertsAction.INSTANCE.name(), GetAlertsAction.NAME)
    }
}
