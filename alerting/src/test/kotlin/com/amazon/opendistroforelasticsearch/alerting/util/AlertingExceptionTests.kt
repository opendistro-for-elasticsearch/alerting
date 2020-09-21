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

package com.amazon.opendistroforelasticsearch.alerting.util

import org.elasticsearch.ElasticsearchSecurityException
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.ESTestCase

class AlertingExceptionTests : ESTestCase() {

    fun `test alerterror wrap`() {
        val ex1 = IndexNotFoundException("index_not_found_exception_message")
        val alterr1 = AlertingException.wrap(ex1)
        assertEquals(ex1.status(), alterr1.status())
        assertTrue(alterr1.message?.startsWith("Configured indices") as Boolean)

        val ex2 = ElasticsearchSecurityException("elasticsearch_security_exception_message", RestStatus.FORBIDDEN)
        val alterr2 = AlertingException.wrap(ex2)
        assertEquals(ex2.status(), alterr2.status())
        assertTrue(alterr2.message?.startsWith("User doesn't have permissions to execute this action") as Boolean)

        val ex3 = ElasticsearchStatusException("elasticsearch_exception_message", RestStatus.BAD_GATEWAY)
        val alterr3 = AlertingException.wrap(ex3)
        assertEquals(ex3.status(), alterr3.status())
        assertTrue(alterr3.message?.startsWith("elasticsearch_exception_message") as Boolean)

        val ex4 = IllegalArgumentException("exception_message")
        val alterr4 = AlertingException.wrap(ex4)
        assertEquals(RestStatus.BAD_REQUEST, alterr4.status())

        val ex5 = RuntimeException("exception_message")
        val alterr5 = AlertingException.wrap(ex5)
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, alterr5.status())
        assertEquals(ex5.message, alterr5.message)
    }
}
