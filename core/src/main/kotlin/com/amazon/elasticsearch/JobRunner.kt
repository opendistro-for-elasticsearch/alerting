/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch

import com.amazon.elasticsearch.model.ScheduledJob

interface JobRunner<T : ScheduledJob> {

    fun runJob(job: T)
}