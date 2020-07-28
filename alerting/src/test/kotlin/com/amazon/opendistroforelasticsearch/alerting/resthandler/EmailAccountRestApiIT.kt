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
