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
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount
import org.elasticsearch.test.junit.annotations.TestLogging

@TestLogging("level:DEBUG", reason = "Debug for tests.")
class EmailAccountRestApiIT : AlertingRestTestCase() {

    fun `test creating an email account`() {
        val emailAccount = EmailAccount(
                name = "test",
                email = "test@email.com",
                host = "smtp.com",
                port = 25,
                method = EmailAccount.MethodType.NONE,
                username = null,
                password = null
        )
        val createdEmailAccount = createEmailAccount(emailAccount = emailAccount)
        assertEquals("Incorrect email account name", createdEmailAccount.name, "test")
        assertEquals("Incorrect email account email", createdEmailAccount.email, "test@email.com")
        assertEquals("Incorrect email account host", createdEmailAccount.host, "smtp.com")
        assertEquals("Incorrect email account port", createdEmailAccount.port, 25)
        assertEquals("Incorrect email account method", createdEmailAccount.method, EmailAccount.MethodType.NONE)
    }

    fun `test updating an email account`() {
        val emailAccount = createEmailAccount()
        val updatedEmailAccount = updateEmailAccount(
                emailAccount.copy(
                        name = "updatedName",
                        port = 465,
                        method = EmailAccount.MethodType.SSL
                )
        )
        assertEquals("Incorrect email account name after update", updatedEmailAccount.name, "updatedName")
        assertEquals("Incorrect email account port after update", updatedEmailAccount.port, 465)
        assertEquals("Incorrect email account method after update", updatedEmailAccount.method, EmailAccount.MethodType.SSL)
    }
}
