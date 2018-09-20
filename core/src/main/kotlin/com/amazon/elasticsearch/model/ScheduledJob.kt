/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.model

import com.amazon.elasticsearch.model.ScheduledJob.Companion.NO_ID
import com.amazon.elasticsearch.model.ScheduledJob.Companion.NO_VERSION
import com.amazon.elasticsearch.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.elasticsearch.model.ScheduledJob.Type
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

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
interface ScheduledJob : ToXContentObject {

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


        /**
         * This function parses the job from a [typed keys](https://www.elastic.co/guide/en/elasticsearch/reference/current/returning-aggregation-type.html)
         * json representation. If the job comes from an elasticsearch index it's [id] and [version] can also be supplied.
         */
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id : String = NO_ID, version : Long = NO_VERSION) : ScheduledJob {
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            val job = xcp.namedObject(ScheduledJob::class.java, xcp.currentName(), null)
            ensureExpectedToken(Token.END_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            return job.fromDocument(id, version)
        }
    }

    /** The id of the job in the [SCHEDULED_JOBS_INDEX] or [NO_ID] if not persisted */
    val id: String

    /** The version of the job in the [SCHEDULED_JOBS_INDEX] or [NO_VERSION] if not persisted  */
    val version : Long

    /** The name of the job */
    val name: String

    /** The type of the job */
    val type: String

    /** Controls whether the job will be scheduled or not  */
    val enabled: Boolean

    /** The schedule for running the job  */
    val schedule: Schedule

    /** The inputs to the job */
    val inputs: List<Input>

    /** The triggering conditions that the inputs must match.  The triggers will contain the actions to take. */
    val conditions: List<Condition>

    /** Copy constructor for persisted jobs */
    fun fromDocument(id: String, version: Long) : ScheduledJob

}