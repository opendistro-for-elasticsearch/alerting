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

import com.amazon.opendistroforelasticsearch.alerting.model.destination.Chime
import com.amazon.opendistroforelasticsearch.alerting.model.destination.CustomWebhook
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Slack
import org.elasticsearch.test.ESTestCase

class DestinationTests : ESTestCase() {

    fun `test chime destination`() {
        val chime = Chime("http://abc.com")
        assertEquals("Url is manipulated", chime.url, "http://abc.com")
    }

    fun `test chime destination with out url`() {
        try {
            Chime("")
            fail("Creating a chime destination with empty url did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test slack destination`() {
        val slack = Slack("http://abc.com")
        assertEquals("Url is manipulated", slack.url, "http://abc.com")
    }

    fun `test slack destination with out url`() {
        try {
            Slack("")
            fail("Creating a slack destination with empty url did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test custom webhook destination with url and no host`() {
        val customWebhook = CustomWebhook("http://abc.com", null, null, -1, null, emptyMap(), emptyMap(), null, null)
        assertEquals("Url is manipulated", customWebhook.url, "http://abc.com")
    }

    fun `test custom webhook destination with host and no url`() {
        try {
            val customWebhook = CustomWebhook(null, null, "abc.com", 80, null, emptyMap(), emptyMap(), null, null)
            assertEquals("host is manipulated", customWebhook.host, "abc.com")
        } catch (ignored: IllegalArgumentException) {
        }
    }

    fun `test custom webhook destination with url and host`() {
        // In this case, url will be given priority
        val customWebhook = CustomWebhook("http://abc.com", null, null, -1, null, emptyMap(), emptyMap(), null, null)
        assertEquals("Url is manipulated", customWebhook.url, "http://abc.com")
    }

    fun `test custom webhook destination with no url and no host`() {
        try {
            CustomWebhook("", null, null, 80, null, emptyMap(), emptyMap(), null, null)
            fail("Creating a custom webhook destination with empty url did not fail.")
        } catch (ignored: IllegalArgumentException) {
        }
    }
}
