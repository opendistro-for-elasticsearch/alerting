/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistro.alerting.settings

import com.amazon.opendistro.alerting.MonitoringPlugin
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.unit.TimeValue

/**
 * Settings specific to [MonitoringPlugin]. These settings include things like history index max age, request timeout, etc...
 */
class MonitoringSettings {
    companion object {
        val MONITORING_ENABLED = Setting.boolSetting(
                "opendistro.monitoring.enabled",
                true,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val INPUT_TIMEOUT = Setting.positiveTimeSetting(
                "opendistro.monitoring.input_timeout",
                TimeValue.timeValueSeconds(30),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val INDEX_TIMEOUT = Setting.positiveTimeSetting(
                "opendistro.monitoring.index_timeout",
                TimeValue.timeValueSeconds(60),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val BULK_TIMEOUT = Setting.positiveTimeSetting(
                "opendistro.monitoring.bulk_timeout",
                TimeValue.timeValueSeconds(120),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_BACKOFF_MILLIS = Setting.positiveTimeSetting(
                "opendistro.monitoring.alert_backoff_millis",
                TimeValue.timeValueMillis(50),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_BACKOFF_COUNT = Setting.intSetting(
                "opendistro.monitoring.alert_backoff_count",
                2,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_ROLLOVER_PERIOD = Setting.positiveTimeSetting(
                "opendistro.monitoring.alert_history_rollover_period",
                TimeValue.timeValueHours(12),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_INDEX_MAX_AGE = Setting.positiveTimeSetting(
                "opendistro.monitoring.alert_history_max_age",
                TimeValue.timeValueHours(24),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_MAX_DOCS = Setting.longSetting(
                "opendistro.monitoring.alert_history_max_docs",
                1000L,
                0L,
                Setting.Property.NodeScope, Setting.Property.Dynamic)

    }
}