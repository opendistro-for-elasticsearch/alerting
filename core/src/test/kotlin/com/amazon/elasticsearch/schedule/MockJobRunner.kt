package com.amazon.elasticsearch.schedule

import com.amazon.elasticsearch.JobRunner
import com.amazon.elasticsearch.model.ScheduledJob
import java.time.Instant

class MockJobRunner : JobRunner {
    var numberOfRun : Int = 0
        private set

    override fun runJob(job: ScheduledJob, periodStart: Instant, periodEnd: Instant) {
        numberOfRun++
    }
}