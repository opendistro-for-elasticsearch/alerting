/*
* Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
*/

package com.amazon.elasticsearch.model

import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

interface Input : ToXContentObject {
    companion object {

        @Throws(IOException::class)
        fun parse(xcp: XContentParser) : Input {
            ensureExpectedToken(Token.START_OBJECT, xcp.currentToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.FIELD_NAME, xcp.nextToken(), xcp::getTokenLocation)
            ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            val input = xcp.namedObject(Input::class.java, xcp.currentName(), null)
            ensureExpectedToken(Token.END_OBJECT, xcp.nextToken(), xcp::getTokenLocation)
            return input
        }
    }

    fun name() : String
}