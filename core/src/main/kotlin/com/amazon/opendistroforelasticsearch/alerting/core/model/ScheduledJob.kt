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

package com.amazon.opendistroforelasticsearch.alerting.core.model

import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.NO_ID
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.NO_VERSION
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException
import java.time.Instant

/**
 * A job that runs periodically in the ElasticSearch cluster.
 *
 * All implementations of [ScheduledJob]s are stored in the [SCHEDULED_JOBS_INDEX] index and are scheduled in a
 * single global Scheduler running on each node. Each implementation should have its own separate APIs for writing,
 * updating and deleting instances of that job type into the [SCHEDULED_JOBS_INDEX] index. The index is periodically
 * scanned for updates which are then scheduled or unscheduled with the Scheduler.
 *
 * Like all documents in Elasticsearch [ScheduledJob]s also have an [id] and a [version].  Jobs that have not been
 * persisted in the cluster should use the special sentinel values [NO_ID] and [NO_VERSION] for these fields.
 */
interface ScheduledJob : ToXContentObject {

    fun toXContentWithType(builder: XContentBuilder): XContentBuilder = toXContent(builder, XCONTENT_WITH_TYPE)

    companion object {
        /** The name of the ElasticSearch index in which we store jobs */
        const val SCHEDULED_JOBS_INDEX = ".opendistro-alerting-config"

        /**
         * The mapping type of [ScheduledJob]s in the ES index. Unrelated to [ScheduledJob.type].
         *
         * This should go away starting ES 7. We use "_doc" for future compatibility as described here:
         * https://www.elastic.co/guide/en/elasticsearch/reference/6.x/removal-of-types.html#_schedule_for_removal_of_mapping_types
         */
        const val SCHEDULED_JOB_TYPE = "_doc"

        const val NO_ID = ""

        const val NO_VERSION = 1L

        private val XCONTENT_WITH_TYPE = ToXContent.MapParams(mapOf("with_type" to "true"))

        /**
         * This function parses the job, delegating to the specific subtype parser registered in the [XContentParser.getXContentRegistry]
         * at runtime.  Each concrete job subclass is expected to register a parser in this registry.
         * The Job's json representation is expected to be of the form:
         *     { "<job_type>" : { <job fields> } }
         *
         * If the job comes from an Elasticsearch index it's [id] and [version] can also be supplied.
         */
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, id: String = NO_ID, version: Long = NO_VERSION): ScheduledJob {
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            val job = xcp.namedObject(ScheduledJob::class.java, xcp.currentName(), null)
            ensureExpectedToken(Token.END_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            return job.fromDocument(id, version)
        }

        /**
         * This function parses the job, but expects the type to be passed in. This is for the specific
         * use case in sweeper where we first want to check if the job is allowed to be swept before
         * trying to fully parse it. If you need to parse a job, you most likely want to use
         * the above parse function.
         */
        @Throws(IOException::class)
        fun parse(xcp: XContentParser, type: String, id: String = NO_ID, version: Long = NO_VERSION): ScheduledJob {
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            val job = xcp.namedObject(ScheduledJob::class.java, type, null)
            ensureExpectedToken(Token.END_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            return job.fromDocument(id, version)
        }
    }

    /** The id of the job in the [SCHEDULED_JOBS_INDEX] or [NO_ID] if not persisted */
    val id: String

    /** The version of the job in the [SCHEDULED_JOBS_INDEX] or [NO_VERSION] if not persisted  */
    val version: Long

    /** The name of the job */
    val name: String

    /** The type of the job */
    val type: String

    /** Controls whether the job will be scheduled or not  */
    val enabled: Boolean

    /** The schedule for running the job  */
    val schedule: Schedule

    /** The last time the job was updated */
    val lastUpdateTime: Instant

    /** The time the job was enabled */
    val enabledTime: Instant?

    /** Copy constructor for persisted jobs */
    fun fromDocument(id: String, version: Long): ScheduledJob
}
