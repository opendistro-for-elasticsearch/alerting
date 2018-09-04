package com.amazon.elasticsearch.model

interface ScheduledJob {
    enum class Type { MONITOR }
    enum class SchemaVersion { V1 }

    companion object {
        const val SCHEDULED_JOBS_INDEX = ".scheduled-jobs"
        const val SCHEDULED_JOB_TYPE = "job"
    }

    val name : String
    val type : Type
    val schemaVersion : SchemaVersion
    val enabled : Boolean
    val schedule : String // TODO: Replace with proper Schedule/Cron Expression type

}