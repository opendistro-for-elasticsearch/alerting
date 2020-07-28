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
