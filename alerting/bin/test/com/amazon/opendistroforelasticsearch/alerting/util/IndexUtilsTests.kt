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

import com.amazon.opendistroforelasticsearch.alerting.parser
import org.elasticsearch.cluster.metadata.IndexMetadata
import org.elasticsearch.test.ESTestCase
import java.lang.NumberFormatException
import kotlin.test.assertFailsWith

class IndexUtilsTests : ESTestCase() {

    fun `test get schema version`() {
        val message = "{\"user\":{ \"name\":\"test\"},\"_meta\":{\"schema_version\": 1}}"

        val schemaVersion = IndexUtils.getSchemaVersion(message)
        assertEquals(1, schemaVersion)
    }

    fun `test get schema version without _meta`() {
        val message = "{\"user\":{ \"name\":\"test\"}}"

        val schemaVersion = IndexUtils.getSchemaVersion(message)
        assertEquals(0, schemaVersion)
    }

    fun `test get schema version without schema_version`() {
        val message = "{\"user\":{ \"name\":\"test\"},\"_meta\":{\"test\": 1}}"

        val schemaVersion = IndexUtils.getSchemaVersion(message)
        assertEquals(0, schemaVersion)
    }

    fun `test get schema version with negative schema_version`() {
        val message = "{\"user\":{ \"name\":\"test\"},\"_meta\":{\"schema_version\": -1}}"

        assertFailsWith(IllegalArgumentException::class, "Expected IllegalArgumentException") {
            IndexUtils.getSchemaVersion(message)
        }
    }

    fun `test get schema version with wrong schema_version`() {
        val message = "{\"user\":{ \"name\":\"test\"},\"_meta\":{\"schema_version\": \"wrong\"}}"

        assertFailsWith(NumberFormatException::class, "Expected NumberFormatException") {
            IndexUtils.getSchemaVersion(message)
        }
    }

    fun `test should update index without original version`() {
        val indexContent = "{\"testIndex\":{\"settings\":{\"index\":{\"creation_date\":\"1558407515699\"," +
                "\"number_of_shards\":\"1\",\"number_of_replicas\":\"1\",\"uuid\":\"t-VBBW6aR6KpJ3XP5iISOA\"," +
                "\"version\":{\"created\":\"6040399\"},\"provided_name\":\"data_test\"}},\"mapping_version\":123," +
                "\"settings_version\":123,\"mappings\":{\"_doc\":{\"properties\":{\"name\":{\"type\":\"keyword\"}}}}}}"
        val newMapping = "{\"_meta\":{\"schema_version\":10},\"properties\":{\"name\":{\"type\":\"keyword\"}}}"
        val index: IndexMetadata = IndexMetadata.fromXContent(parser(indexContent))

        val shouldUpdateIndex = IndexUtils.shouldUpdateIndex(index, newMapping)
        assertTrue(shouldUpdateIndex)
    }

    fun `test should update index with lagged version`() {
        val indexContent = "{\"testIndex\":{\"settings\":{\"index\":{\"creation_date\":\"1558407515699\"," +
                "\"number_of_shards\":\"1\",\"number_of_replicas\":\"1\",\"uuid\":\"t-VBBW6aR6KpJ3XP5iISOA\"," +
                "\"version\":{\"created\":\"6040399\"},\"provided_name\":\"data_test\"}},\"mapping_version\":123," +
                "\"settings_version\":123,\"mappings\":{\"_doc\":{\"_meta\":{\"schema_version\":1},\"properties\":" +
                "{\"name\":{\"type\":\"keyword\"}}}}}}"
        val newMapping = "{\"_meta\":{\"schema_version\":10},\"properties\":{\"name\":{\"type\":\"keyword\"}}}"
        val index: IndexMetadata = IndexMetadata.fromXContent(parser(indexContent))

        val shouldUpdateIndex = IndexUtils.shouldUpdateIndex(index, newMapping)
        assertTrue(shouldUpdateIndex)
    }

    fun `test should update index with same version`() {
        val indexContent = "{\"testIndex\":{\"settings\":{\"index\":{\"creation_date\":\"1558407515699\"," +
                "\"number_of_shards\":\"1\",\"number_of_replicas\":\"1\",\"uuid\":\"t-VBBW6aR6KpJ3XP5iISOA\"," +
                "\"version\":{\"created\":\"6040399\"},\"provided_name\":\"data_test\"}},\"mappings\":" +
                "{\"_doc\":{\"_meta\":{\"schema_version\":1},\"properties\":{\"name\":{\"type\":\"keyword\"}}}}}}"
        val newMapping = "{\"_meta\":{\"schema_version\":1},\"properties\":{\"name\":{\"type\":\"keyword\"}}}"
        val index: IndexMetadata = IndexMetadata.fromXContent(parser(indexContent))

        val shouldUpdateIndex = IndexUtils.shouldUpdateIndex(index, newMapping)
        assertFalse(shouldUpdateIndex)
    }
}
