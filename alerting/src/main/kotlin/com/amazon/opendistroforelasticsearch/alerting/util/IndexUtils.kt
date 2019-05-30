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

package com.amazon.opendistroforelasticsearch.alerting.util

import com.amazon.opendistroforelasticsearch.alerting.alerts.AlertIndices
import com.amazon.opendistroforelasticsearch.alerting.core.ScheduledJobIndices
import com.amazon.opendistroforelasticsearch.alerting.core.util.SchemaVersionUtils.Companion.getSchemaVersion

class IndexUtils {

    companion object {
        var scheduledJobIndexSchemaVersion: Int
            private set
        var alertIndexSchemaVersion: Int
            private set

        var scheduledJobIndexUpdated: Boolean = false
            private set
        var alertIndexUpdated: Boolean = false
            private set
        var lastUpdatedHistoryIndex: String? = null

        init {
            scheduledJobIndexSchemaVersion = getSchemaVersion(ScheduledJobIndices.scheduledJobMappings())
            alertIndexSchemaVersion = getSchemaVersion(AlertIndices.alertMapping())
        }

        @JvmStatic
        fun scheduledJobIndexUpdated() {
            scheduledJobIndexUpdated = true
        }

        @JvmStatic
        fun alertIndexUpdated() {
            alertIndexUpdated = true
        }
    }
}
