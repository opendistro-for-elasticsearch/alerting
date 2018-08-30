package com.amazon.elasticsearch.model

interface ScheduledJob {
    enum class Type { MONITOR }
    enum class SchemaVersion { V1 }

    val name : String
    val type : Type
    val schemaVersion : SchemaVersion
    val enabled : Boolean
    val schedule : String // TODO: Replace with proper Schedule/Cron Expression type

}