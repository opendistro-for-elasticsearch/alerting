/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import org.elasticsearch.common.io.stream.StreamInput
import org.elasticsearch.common.io.stream.StreamOutput
import org.elasticsearch.common.io.stream.Writeable
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.common.xcontent.ToXContentObject
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentParser
import org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken
import java.io.IOException

data class User(
    val name: String,
    val backendRoles: List<String>,
    val roles: List<String>,
    val customAttNames: List<String>
) : Writeable, ToXContentObject {

    @Throws(IOException::class)
    constructor(sin: StreamInput): this(
        name = sin.readString(),
        backendRoles = sin.readStringList(),
        roles = sin.readStringList(),
        customAttNames = sin.readStringList()
    )

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
                .field(NAME_FIELD, name)
                .field(BACKEND_ROLES_FIELD, backendRoles)
                .field(ROLES_FIELD, roles)
                .field(CUSTOM_ATTRIBUTE_NAMES_FIELD, customAttNames)
        return builder.endObject()
    }
    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeString(name)
        out.writeStringCollection(backendRoles)
        out.writeStringCollection(roles)
        out.writeStringCollection(customAttNames)
    }

    companion object {
        const val NAME_FIELD = "name"
        const val BACKEND_ROLES_FIELD = "backend_roles"
        const val ROLES_FIELD = "roles"
        const val CUSTOM_ATTRIBUTE_NAMES_FIELD = "custom_attribute_names"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): User {
            var name = ""
            val backendRoles: MutableList<String> = mutableListOf()
            val roles: MutableList<String> = mutableListOf()
            val customAttNames: MutableList<String> = mutableListOf()

            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()
                when (fieldName) {
                    NAME_FIELD -> name = xcp.text()
                    ROLES_FIELD -> {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != XContentParser.Token.END_ARRAY) {
                            roles.add(xcp.text())
                        }
                    }
                    BACKEND_ROLES_FIELD -> {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != XContentParser.Token.END_ARRAY) {
                            backendRoles.add(xcp.text())
                        }
                    }
                    CUSTOM_ATTRIBUTE_NAMES_FIELD -> {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, xcp.currentToken(), xcp::getTokenLocation)
                        while (xcp.nextToken() != XContentParser.Token.END_ARRAY) {
                            customAttNames.add(xcp.text())
                        }
                    }
                }
            }
            return User(name, backendRoles, roles, customAttNames)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): User {
            return User(sin)
        }
    }
}
