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

package com.amazon.opendistroforelasticsearch.alerting.settings

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.unit.TimeValue

/**
 * settings specific to [AlertingPlugin]. These settings include things like history index max age, request timeout, etc...
 */
class AlertingSettings {

    companion object {
        const val MONITOR_MAX_INPUTS = 1
        const val MONITOR_MAX_TRIGGERS = 10

        val ALERTING_MAX_MONITORS = Setting.intSetting(
                "opendistro.alerting.monitor.max_monitors",
                1000,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val INPUT_TIMEOUT = Setting.positiveTimeSetting(
                "opendistro.alerting.input_timeout",
                TimeValue.timeValueSeconds(30),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val INDEX_TIMEOUT = Setting.positiveTimeSetting(
                "opendistro.alerting.index_timeout",
                TimeValue.timeValueSeconds(60),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val BULK_TIMEOUT = Setting.positiveTimeSetting(
                "opendistro.alerting.bulk_timeout",
                TimeValue.timeValueSeconds(120),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_BACKOFF_MILLIS = Setting.positiveTimeSetting(
                "opendistro.alerting.alert_backoff_millis",
                TimeValue.timeValueMillis(50),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_BACKOFF_COUNT = Setting.intSetting(
                "opendistro.alerting.alert_backoff_count",
                2,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val MOVE_ALERTS_BACKOFF_MILLIS = Setting.positiveTimeSetting(
                "opendistro.alerting.move_alerts_backoff_millis",
                TimeValue.timeValueMillis(250),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val MOVE_ALERTS_BACKOFF_COUNT = Setting.intSetting(
                "opendistro.alerting.move_alerts_backoff_count",
                3,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_ROLLOVER_PERIOD = Setting.positiveTimeSetting(
                "opendistro.alerting.alert_history_rollover_period",
                TimeValue.timeValueHours(12),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_INDEX_MAX_AGE = Setting.positiveTimeSetting(
                "opendistro.alerting.alert_history_max_age",
                TimeValue.timeValueHours(24),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val ALERT_HISTORY_MAX_DOCS = Setting.longSetting(
                "opendistro.alerting.alert_history_max_docs",
                1000L,
                0L,
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val REQUEST_TIMEOUT = Setting.positiveTimeSetting(
                "opendistro.alerting.request_timeout",
                TimeValue.timeValueSeconds(10),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
        val MAX_ACTION_THROTTLE_VALUE = Setting.positiveTimeSetting(
                "opendistro.alerting.action_throttle_max_value",
                TimeValue.timeValueHours(24),
                Setting.Property.NodeScope, Setting.Property.Dynamic)
    }
}
