package com.amazon.elasticsearch.monitoring

import org.elasticsearch.test.ESTestCase
import java.lang.IllegalArgumentException
import java.time.Instant


class MonitorTests: ESTestCase() {

    fun `test enabled time`() {
        val monitor = randomMonitor()
        val enabled_monitor = monitor.copy(enabled = true, enabledTime = Instant.now())
        try {
            val disable_monitor_fail = enabled_monitor.copy(enabled = false)
            fail("Disabling monitor with enabled time set should fail.")
        } catch (e: IllegalArgumentException) {
        }

        val disabled_monitor = monitor.copy(enabled = false, enabledTime = null)

        try {
            val enabled_monitor_fail = disabled_monitor.copy(enabled = true)
            fail("Enabling monitor without enabled time should fail")
        } catch (e: IllegalArgumentException) {
        }
    }
}