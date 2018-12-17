package com.amazon.elasticsearch.monitoring.model

import com.amazon.elasticsearch.monitoring.randomAlert
import org.elasticsearch.test.ESTestCase

class AlertTests: ESTestCase() {
    fun `test alert as template args`() {
        val alert = randomAlert().copy(acknowledgedTime = null, lastNotificationTime = null)

        val templateArgs = alert.asTemplateArg()

        assertEquals("Template args state does not match", templateArgs["state"], alert.state.toString())
        assertEquals("Template args error message does not match", templateArgs["error_message"], alert.errorMessage)
        assertEquals("Template args acknowledged time does not match", templateArgs["acknowledged_time"], alert.acknowledgedTime?.toEpochMilli())
        assertEquals("Template args last notification time does not match", templateArgs["last_notification_time"], alert.lastNotificationTime?.toEpochMilli())
    }

    fun `test alert acknowledged`() {
        val ackAlert = randomAlert().copy(state = Alert.State.ACKNOWLEDGED)
        assertTrue("Alert is not acknowledged", ackAlert.isAcknowledged())

        val activeAlert = randomAlert().copy(state = Alert.State.ACTIVE)
        assertFalse("Alert is acknowledged", activeAlert.isAcknowledged())
    }
}