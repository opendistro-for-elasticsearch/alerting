/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentFragment
import org.elasticsearch.common.xcontent.XContentBuilder

/**
 * A job that runs periodically in the ElasticSearch cluster.
 *
 * A [ScheduledJob] has 3 major components:
 *  * A [schedule] which specifies when the job runs.
 *  * One or more [inputs] which specifies the input data for the job (such as search query, or static values)
 *  * One or more [triggers] which specifies the conditions that the input must match.
 *  * One or more actions to take if the trigger condition is satisfied (i.e. the trigger "fires")
 *
 * There can be many [Type]s of [ScheduledJob]s (identified by the [type] field) that perform different kinds of work.
 * All of the different types of jobs are scheduled and run in a single global Scheduler running on each node. Each
 * [Type] of job will implement it's own external APIs for user's to read, write, query and otherwise manipulate jobs.
 *
 * All types of [ScheduledJob]s are stored as documents in a special index in the cluster (see [SCHEDULED_JOBS_INDEX]).
 * Like all documents stored in the cluster they have an [id] and a [version].  Jobs that have not been persisted in the
 * cluster should use the special sentinel values [NO_ID] and [NO_VERSION] for these fields.
 */
interface ScheduledJob : ToXContentFragment {

    /** Enum of all supported types of [ScheduledJob]s. */
    enum class Type {
        MONITOR
    }

    companion object {
        /** The name of the ElasticSearch index in which we store jobs */
        const val SCHEDULED_JOBS_INDEX = ".scheduled-jobs"

        /**
         * The mapping type of [ScheduledJob]s in the ES index. Unrelated to [Type].
         *
         * This should go away starting ES 7. We use "_doc" for future compatibility as described here:
         * https://www.elastic.co/guide/en/elasticsearch/reference/6.x/removal-of-types.html#_schedule_for_removal_of_mapping_types
         */
        const val SCHEDULED_JOB_TYPE = "_doc"

        const val NO_ID = ""
        const val NO_VERSION = 1L
        const val TYPE_FIELD = "type"
    }

    /** The id of the job in the [SCHEDULED_JOBS_INDEX] or [NO_ID] if not persisted */
    val id: String

    /** The version of the job in the [SCHEDULED_JOBS_INDEX] or [NO_VERSION] if not persisted  */
    val version : Long

    /** The name of the job */
    val name: String

    /** The type of the job */
    val type: Type

    /** Controls whether the job will be scheduled or not  */
    val enabled: Boolean

    /** The schedule for running the job  */
    val schedule: String // TODO: Replace with proper Schedule/Cron Expression type

    /** The inputs to the job */
    val inputs: List<String> // TODO: Replace with proper Input type

    /** The triggering conditions that the inputs must match.  The triggers will contain the actions to take. */
    val triggers: List<String> // TODO: Replace with proper Trigger type

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        return builder.field(TYPE_FIELD, type)
    }
}