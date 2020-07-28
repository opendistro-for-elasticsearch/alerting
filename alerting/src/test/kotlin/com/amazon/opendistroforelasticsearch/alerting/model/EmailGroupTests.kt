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
