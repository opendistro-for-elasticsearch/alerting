package com.amazon.elasticsearch.schedule

import com.amazon.elasticsearch.JobRunner
import com.amazon.elasticsearch.model.ScheduledJob

class MockJobRunner : JobRunner<ScheduledJob> {
    var numberOfRun : Int = 0
        private set

    override fun runJob(job: ScheduledJob) {
        numberOfRun++
    }
}