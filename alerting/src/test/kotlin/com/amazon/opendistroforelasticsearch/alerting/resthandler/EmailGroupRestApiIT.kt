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

import com.amazon.opendistroforelasticsearch.alerting.AlertingRestTestCase
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailEntry
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import org.elasticsearch.test.junit.annotations.TestLogging

@TestLogging("level:DEBUG", reason = "Debug for tests.")
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
}
