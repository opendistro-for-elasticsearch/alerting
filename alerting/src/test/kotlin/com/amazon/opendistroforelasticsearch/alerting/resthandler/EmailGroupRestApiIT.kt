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

package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin.Companion.EMAIL_GROUP_BASE_URI
import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.makeRequest
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailEntry
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import com.amazon.opendistroforelasticsearch.alerting.randomEmailGroup
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.ResponseException
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.test.ESTestCase
import org.elasticsearch.test.junit.annotations.TestLogging

@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class EmailGroupRestApiIT : AlertingRestTestCase() {

    fun `test creating an email group`() {
        val emailGroup = EmailGroup(
                name = "test",
                emails = listOf(EmailEntry("test@email.com"))
        )
        val createdEmailGroup = createEmailGroup(emailGroup = emailGroup)
        assertEquals("Incorrect email group name", createdEmailGroup.name, "test")
        assertEquals("Incorrect email group email entry", createdEmailGroup.emails[0].email, "test@email.com")
    }

    fun `test creating an email group with PUT fails`() {
        try {
            val emailGroup = randomEmailGroup()
            client().makeRequest("PUT", EMAIL_GROUP_BASE_URI, emptyMap(), emailGroup.toHttpEntity())
            fail("Expected 405 Method Not Allowed respone")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.METHOD_NOT_ALLOWED, e.response.restStatus())
        }
    }

    fun `test updating an email group`() {
        val emailGroup = createEmailGroup()
        val updatedEmailGroup = updateEmailGroup(
                emailGroup.copy(
                        name = "updatedName",
                        emails = listOf(EmailEntry("test@email.com"))
                )
        )
        assertEquals("Incorrect email group name after update", updatedEmailGroup.name, "updatedName")
        assertEquals("Incorrect email group email entry after update", updatedEmailGroup.emails[0].email, "test@email.com")
    }

    fun `test getting an email group`() {
        val emailGroup = createRandomEmailGroup()
        val storedEmailGroup = getEmailGroup(emailGroup.id)
        assertEquals("Indexed and retrieved email group differ", emailGroup, storedEmailGroup)
    }

    fun `test getting an email group that doesn't exist`() {
        try {
            getEmailGroup(randomAlphaOfLength(20))
            fail("Expected response exception")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.NOT_FOUND, e.response.restStatus())
        }
    }

    fun `test checking if an email group exists`() {
        val emailGroup = createRandomEmailGroup()

        val headResponse = client().makeRequest("HEAD", "$EMAIL_GROUP_BASE_URI/${emailGroup.id}")
        assertEquals("Unable to HEAD email group", RestStatus.OK, headResponse.restStatus())
        assertNull("Response contains unexpected body", headResponse.entity)
    }

    fun `test checking if a non-existent email group exists`() {
        val headResponse = client().makeRequest("HEAD", "$EMAIL_GROUP_BASE_URI/foobar")
        assertEquals("Unexpected status", RestStatus.NOT_FOUND, headResponse.restStatus())
    }

    fun `test deleting an email group`() {
        val emailGroup = createRandomEmailGroup()

        val deleteResponse = client().makeRequest("DELETE", "$EMAIL_GROUP_BASE_URI/${emailGroup.id}")
        assertEquals("Delete failed", RestStatus.OK, deleteResponse.restStatus())

        val headResponse = client().makeRequest("HEAD", "$EMAIL_GROUP_BASE_URI/${emailGroup.id}")
        assertEquals("Deleted email group still exists", RestStatus.NOT_FOUND, headResponse.restStatus())
    }

    fun `test deleting an email group that doesn't exist`() {
        try {
            client().makeRequest("DELETE", "$EMAIL_GROUP_BASE_URI/foobar")
            fail("Expected 404 response exception")
        } catch (e: ResponseException) {
            assertEquals(RestStatus.NOT_FOUND, e.response.restStatus())
        }
    }

    fun `test querying an email group that exists`() {
        val emailGroup = createRandomEmailGroup()

        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", emailGroup.id)).toString()
        val searchResponse = client().makeRequest(
            "GET",
            "$EMAIL_GROUP_BASE_URI/_search",
            emptyMap(),
            NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search email group failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Map<String, Any>>
        val numberOfDocsFound = hits["total"]?.get("value")
        assertEquals("Email group not found during search", 1, numberOfDocsFound)
    }

    fun `test querying an email group that exists with POST`() {
        val emailGroup = createRandomEmailGroup()

        val search = SearchSourceBuilder().query(QueryBuilders.termQuery("_id", emailGroup.id)).toString()
        val searchResponse = client().makeRequest(
            "POST",
            "$EMAIL_GROUP_BASE_URI/_search",
            emptyMap(),
            NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search email group failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Map<String, Any>>
        val numberOfDocsFound = hits["total"]?.get("value")
        assertEquals("Email group not found during search", 1, numberOfDocsFound)
    }

    fun `test querying an email group that doesn't exist`() {
        // Create a random email group to create the ScheduledJob index. Otherwise the test will fail with a 404 index not found error.
        createRandomEmailGroup()
        val search = SearchSourceBuilder()
            .query(QueryBuilders.termQuery(
                ESTestCase.randomAlphaOfLength(5),
                ESTestCase.randomAlphaOfLength(5)
            )).toString()

        val searchResponse = client().makeRequest(
            "GET",
            "$EMAIL_GROUP_BASE_URI/_search",
            emptyMap(),
            NStringEntity(search, ContentType.APPLICATION_JSON))
        assertEquals("Search email group failed", RestStatus.OK, searchResponse.restStatus())
        val xcp = createParser(XContentType.JSON.xContent(), searchResponse.entity.content)
        val hits = xcp.map()["hits"]!! as Map<String, Map<String, Any>>
        val numberOfDocsFound = hits["total"]?.get("value")
        assertEquals("Email group found during search when no document was present", 0, numberOfDocsFound)
    }
}
