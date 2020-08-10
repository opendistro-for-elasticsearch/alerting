package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.test.ESTestCase
import org.junit.Assert

class DeleteDestinationActionTests : ESTestCase() {

    fun `test delete destination action name`() {
        Assert.assertNotNull(DeleteDestinationAction.INSTANCE.name())
        Assert.assertEquals(DeleteDestinationAction.INSTANCE.name(), DeleteDestinationAction.NAME)
    }
}
