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

package com.amazon.opendistroforelasticsearch.alerting.model

import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount
import org.elasticsearch.test.ESTestCase

class EmailAccountTests : ESTestCase() {

    fun `test email account`() {
        val emailAccount = EmailAccount(
                name = "test",
                email = "test@email.com",
                host = "smtp.com",
                port = 25,
                method = EmailAccount.MethodType.NONE,
                username = null,
                password = null
        )
        assertEquals("Email account name was changed", emailAccount.name, "test")
        assertEquals("Email account email was changed", emailAccount.email, "test@email.com")
        assertEquals("Email account host was changed", emailAccount.host, "smtp.com")
        assertEquals("Email account port was changed", emailAccount.port, 25)
        assertEquals("Email account method was changed", emailAccount.method, EmailAccount.MethodType.NONE)
    }
}
