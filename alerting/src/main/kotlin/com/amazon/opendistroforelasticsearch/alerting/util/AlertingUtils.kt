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

import com.amazon.opendistroforelasticsearch.alerting.destination.message.BaseMessage
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.model.destination.Destination
import com.amazon.opendistroforelasticsearch.alerting.settings.DestinationSettings
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.rest.RestStatus
import inet.ipaddr.IPAddressString

/**
 * RFC 5322 compliant pattern matching: https://www.ietf.org/rfc/rfc5322.txt
 * Regex was based off of this post: https://stackoverflow.com/a/201378
 */
fun isValidEmail(email: String): Boolean {
    val validEmailPattern = Regex("(?:[a-z0-9!#\$%&'*+\\/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+\\/=?^_`{|}~-]+)*" +
        "|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")" +
        "@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" +
        "|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}" +
        "(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:" +
        "(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])",
        RegexOption.IGNORE_CASE)

    return validEmailPattern.matches(email)
}

/** Allowed Destinations are ones that are specified in the [DestinationSettings.ALLOW_LIST] setting. */
fun Destination.isAllowed(allowList: List<String>): Boolean = allowList.contains(this.type.value)

fun BaseMessage.isHostInDenylist(networks: List<String>): Boolean {
    val ipStr = IPAddressString(this.uri.host)
    for (network in networks) {
        val netStr = IPAddressString(network)
        if (netStr.contains(ipStr)) {
            return true
        }
    }
    return false
}

/**
  1. If filterBy is enabled
     a) Don't allow to create monitor/ destination (throw error) if the logged-on user has no backend roles configured.
  2. If filterBy is enabled & monitors are created when filterBy is disabled:
     a) If backend_roles are saved with config, results will get filtered and data is shown
     b) If backend_roles are not saved with monitor config, results will get filtered and no monitors
        will be displayed.
     c) Users can edit and save the monitors to associate their backend_roles.
  3. If filterBy is enabled & monitors are created by older version:
     a) No User details are present on monitor.
     b) No monitors will be displayed.
     c) Users can edit and save the monitors to associate their backend_roles.
 */
fun <T : Any> checkFilterByUserBackendRoles(filterByEnabled: Boolean, user: User?, actionListener: ActionListener<T>): Boolean {
    if (filterByEnabled) {
        if (user == null) {
            actionListener.onFailure(AlertingException.wrap(
                    ElasticsearchStatusException(
                            "Filter by user backend roles is not enabled with security disabled.", RestStatus.FORBIDDEN
                    )
            ))
            return false
        } else if (user.backendRoles.isNullOrEmpty()) {
            actionListener.onFailure(AlertingException.wrap(
                    ElasticsearchStatusException("User doesn't have backend roles configured. Contact administrator.", RestStatus.FORBIDDEN)
            ))
            return false
        }
    }
    return true
}

/**
 * If FilterBy is enabled, this function verifies that the requester user has FilterBy permissions to access
 * the resource. If FilterBy is disabled, we will assume the user has permissions and return true.
 *
 * This check will later to moved to the security plugin.
 */
fun <T : Any> checkUserFilterByPermissions(
    filterByEnabled: Boolean,
    requesterUser: User?,
    resourceUser: User?,
    actionListener: ActionListener<T>,
    resourceType: String,
    resourceId: String
): Boolean {

    if (!filterByEnabled) return true

    val resourceBackendRoles = resourceUser?.backendRoles
    val requesterBackendRoles = requesterUser?.backendRoles

    if (resourceBackendRoles == null || requesterBackendRoles == null || resourceBackendRoles.intersect(requesterBackendRoles).isEmpty()) {
        actionListener.onFailure(AlertingException.wrap(
            ElasticsearchStatusException("Do not have permissions to resource, $resourceType, with id, $resourceId",
                RestStatus.FORBIDDEN)
        ))
        return false
    }
    return true
}

fun Monitor.isAggregationMonitor(): Boolean = this.monitorType == Monitor.MonitorType.AGGREGATION_MONITOR
