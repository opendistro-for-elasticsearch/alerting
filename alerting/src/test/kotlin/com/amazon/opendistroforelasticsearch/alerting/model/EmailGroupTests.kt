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

import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailEntry
import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailGroup
import org.elasticsearch.test.ESTestCase

class EmailGroupTests : ESTestCase() {

    fun `test email group`() {
        val emailGroup = EmailGroup(
                name = "test",
                emails = listOf(EmailEntry("test@email.com"))
        )
        assertEquals("Email group name was changed", emailGroup.name, "test")
        assertEquals("Email group emails count was changed", emailGroup.emails.size, 1)
        assertEquals("Email group email entry was changed", emailGroup.emails[0].email, "test@email.com")
    }
}
