package com.amazon.elasticsearch.Settings

import com.amazon.elasticsearch.model.ScheduledJob
import org.elasticsearch.common.settings.Setting
import org.elasticsearch.common.unit.TimeValue

/**
 * Settings used for [ScheduledJob]'s. These include back off settings, retry counts, timeouts etc...
 */

val REQUEST_TIMEOUT = Setting.positiveTimeSetting(
        "aes.scheduled_jobs.request_timeout",
        TimeValue.timeValueSeconds(10),
        Setting.Property.NodeScope, Setting.Property.Dynamic)

val SWEEP_BACKOFF_MILLIS = Setting.positiveTimeSetting(
        "aes.scheduled_jobs.sweeper.backoff_millis",
        TimeValue.timeValueMillis(50),
        Setting.Property.NodeScope, Setting.Property.Dynamic)

val SWEEP_BACKOFF_RETRY_COUNT = Setting.intSetting(
        "aes.scheduled_jobs.retry_count",
        3,
        Setting.Property.NodeScope, Setting.Property.Dynamic)

val SWEEP_PERIOD = Setting.positiveTimeSetting(
        "aes.scheduled_jobs.sweeper.period",
        TimeValue.timeValueMinutes(5),
        Setting.Property.NodeScope, Setting.Property.Dynamic)

val SWEEP_PAGE_SIZE = Setting.intSetting(
        "aes.scheduled_jobs.sweeper.page_size",
        100,
        Setting.Property.NodeScope, Setting.Property.Dynamic)
