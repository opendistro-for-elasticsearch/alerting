package com.amazon.elasticsearch.monitoring.settings

import com.amazon.elasticsearch.monitoring.MonitoringPlugin
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.unit.TimeValue

/**
 * Settings specific to [MonitoringPlugin]. These settings include things like history index max age, request timeout, etc...
 */
class MonitoringSettings {
    companion object {
        val MONITORING_ENABLED = Setting.boolSetting(
                "aes.monitoring.enabled",
                false,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val INPUT_TIMEOUT = Setting.positiveTimeSetting(
                "aes.monitoring.input_timeout",
                TimeValue.timeValueSeconds(30),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val INDEX_TIMEOUT = Setting.positiveTimeSetting(
                "aes.monitoring.index_timeout",
                TimeValue.timeValueSeconds(60),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val BULK_TIMEOUT = Setting.positiveTimeSetting(
                "aes.monitoring.bulk_timeout",
                TimeValue.timeValueSeconds(120),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_BACKOFF_MILLIS = Setting.positiveTimeSetting(
                "aes.monitoring.alert_backoff_millis",
                TimeValue.timeValueMillis(50),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_BACKOFF_COUNT = Setting.intSetting(
                "aes.monitoring.alert_backoff_count",
                2,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_ROLLOVER_PERIOD = Setting.positiveTimeSetting(
                "aes.monitoring.alert_history_rollover_period",
                TimeValue.timeValueHours(12),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_INDEX_MAX_AGE = Setting.positiveTimeSetting(
                "aes.monitoring.alert_history_max_age",
                TimeValue.timeValueHours(24),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_MAX_DOCS = Setting.longSetting(
                "aes.monitoring.alert_history_max_docs",
                1000L,
                0L,
                Setting.Property.NodeScope, Setting.Property.Dynamic)

    }
}