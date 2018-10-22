/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch

import com.amazon.elasticsearch.model.ScheduledJob
import java.time.Instant

interface JobRunner<T : ScheduledJob> {
// threadPool.generic().submit(Runnable { runner.runJob(); }
    fun runJob(job: T, periodStart : Instant, periodEnd: Instant)
}