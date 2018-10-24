/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch

import com.amazon.elasticsearch.model.ScheduledJob
import java.time.Instant

interface JobRunner {
    fun runJob(job: ScheduledJob, periodStart : Instant, periodEnd: Instant)
}