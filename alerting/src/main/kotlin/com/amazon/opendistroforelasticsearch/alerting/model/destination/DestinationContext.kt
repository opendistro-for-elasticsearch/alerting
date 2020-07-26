package com.amazon.opendistroforelasticsearch.alerting.model.destination

import com.amazon.opendistroforelasticsearch.alerting.model.destination.email.EmailAccount

/**
 * DestinationContext is a value object that contains additional context information needed at runtime to publish to a destination.
 * For now it only contains the information retrieved from documents by ID for Email (such as email account and email group recipients).
 */
data class DestinationContext(
    val emailAccount: EmailAccount? = null,
    val recipients: List<String> = emptyList()
)
