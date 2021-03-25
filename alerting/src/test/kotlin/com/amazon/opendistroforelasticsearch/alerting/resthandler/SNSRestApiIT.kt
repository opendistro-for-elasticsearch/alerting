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
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.model.destination.SNS
import com.amazon.opendistroforelasticsearch.alerting.randomUser
import com.amazon.opendistroforelasticsearch.alerting.util.DestinationType
import org.elasticsearch.test.junit.annotations.TestLogging
import org.junit.Assert
import java.time.Instant

@TestLogging("level:DEBUG", reason = "Debug for tests.")
@Suppress("UNCHECKED_CAST")
class SNSRestApiIT : AlertingRestTestCase() {

    fun `test creating a sns destination`() {
        val sns = SNS("arn:aws:sns:us-west-2:475347751589:test-notification", "arn:aws:iam::853806060000:role/domain/abc")
        val destination = Destination(
            type = DestinationType.SNS,
            name = "test",
            user = randomUser(),
            lastUpdateTime = Instant.now(),
            chime = null,
            slack = null,
            sns = sns,
            customWebhook = null,
            email = null)
        val createdDestination = createDestination(destination = destination)
        assertEquals("Incorrect destination name", createdDestination.name, "test")
        assertEquals("Incorrect destination type", createdDestination.type, DestinationType.SNS)
        Assert.assertNotNull("sns object should not be null", createdDestination.sns)
    }

    fun `test updating a sns destination`() {
        val destination = createDestination()
        val sns = SNS("arn:aws:sns:us-west-2:475347751589:test-notification", "arn:aws:iam::853806060000:role/domain/abc")
        var updatedDestination = updateDestination(
            destination.copy(name = "updatedName", sns = sns, type = DestinationType.SNS))
        assertEquals("Incorrect destination name after update", updatedDestination.name, "updatedName")
        assertEquals("Incorrect destination ID after update", updatedDestination.id, destination.id)
        assertEquals("Incorrect destination type after update", updatedDestination.type, DestinationType.SNS)
        assertEquals("Incorrect destination sns topic arn after update",
            "arn:aws:sns:us-west-2:475347751589:test-notification", updatedDestination.sns?.topicARN)
        val updatedSns = SNS("arn:aws:sns:us-west-3:475347751589:test-notification", "arn:aws:iam::123456789012:role/domain/abc")
        updatedDestination = updateDestination(
            destination.copy(name = "updatedName", sns = updatedSns, type = DestinationType.SNS))
        assertEquals("Incorrect destination sns topic arn after update",
            "arn:aws:sns:us-west-3:475347751589:test-notification", updatedDestination.sns?.topicARN)
    }
}
