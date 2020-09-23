package com.amazon.opendistroforelasticsearch.alerting.model

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import java.io.IOException

data class Table(
    val sortOrder: String,
    val sortString: String,
    val missing: String?,
    val size: Int,
    val startIndex: Int
) : Writeable {

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
            sin.readString(), // sortOrder
            sin.readString(), // sortString
            sin.readOptionalString(), // missing
            sin.readInt(), // size
            sin.readInt() // startIndex
    )

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(sortOrder)
        out.writeString(sortString)
        out.writeOptionalString(missing)
        out.writeInt(size)
        out.writeInt(startIndex)
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): Table {
            return Table(sin)
        }
    }
}
