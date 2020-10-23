/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import com.amazon.opendistroforelasticsearch.alerting.core.model.SearchInput
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.NestedQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder

/**
 * AD monitor is search input monitor on top of anomaly result index. This method will return
 * true if monitor input only contains anomaly result index.
 */
fun isADMonitor(monitor: Monitor): Boolean {
    // If monitor has other input than AD result index, it's not AD monitor
    if (monitor.inputs.size != 1) {
        return false
    }
    val input = monitor.inputs[0]
    // AD monitor can only have 1 anomaly result index.
    if (input is SearchInput && input.indices.size == 1 && input.indices[0] == ".opendistro-anomaly-results*") {
        return true
    }
    return false
}

fun addUserBackendRolesFilter(user: User?, searchSourceBuilder: SearchSourceBuilder): SearchSourceBuilder {
    var boolQueryBuilder = BoolQueryBuilder()
    val userFieldName = "user"
    val userBackendRoleFieldName = "user.backend_roles.keyword"
    if (user == null) {
        // For old monitor and detector, they have no user field
        val userRolesFilterQuery = QueryBuilders.existsQuery(userFieldName)
        val nestedQueryBuilder = NestedQueryBuilder(userFieldName, userRolesFilterQuery, ScoreMode.None)
        boolQueryBuilder.mustNot(nestedQueryBuilder)
    } else if (user.backendRoles.isNullOrEmpty()) {
        // For simple FGAC user, they may have no backend roles, these users should be able to see detectors
        // of other users whose backend role is empty.
        val userRolesFilterQuery = QueryBuilders.existsQuery(userBackendRoleFieldName)
        val nestedQueryBuilder = NestedQueryBuilder(userFieldName, userRolesFilterQuery, ScoreMode.None)

        val userExistsQuery = QueryBuilders.existsQuery(userFieldName)
        val userExistsNestedQueryBuilder = NestedQueryBuilder(userFieldName, userExistsQuery, ScoreMode.None)

        boolQueryBuilder.mustNot(nestedQueryBuilder)
        boolQueryBuilder.must(userExistsNestedQueryBuilder)
    } else {
        // For normal case, user should have backend roles.
        val userRolesFilterQuery = QueryBuilders.termsQuery(userBackendRoleFieldName, user.backendRoles)
        val nestedQueryBuilder = NestedQueryBuilder(userFieldName, userRolesFilterQuery, ScoreMode.None)
        boolQueryBuilder.must(nestedQueryBuilder)
    }
    val query = searchSourceBuilder.query()
    if (query == null) {
        searchSourceBuilder.query(boolQueryBuilder)
    } else {
        (query as BoolQueryBuilder).filter(boolQueryBuilder)
    }
    return searchSourceBuilder
}
