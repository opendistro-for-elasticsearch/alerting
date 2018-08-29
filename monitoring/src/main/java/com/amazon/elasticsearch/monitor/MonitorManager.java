/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazon.elasticsearch.monitor;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;

/**
 * The monitor manager currently contains all the business logic for creating, deleting, retreiving, and running the policies
 * and policies.
 */
public class MonitorManager {

    private Client client;
    private Logger logger;
    private Settings settings;
    private Locale aLocale = new Locale("en", "US");

    private final String SCHEDULED_JOBS_INDEX = ".scheduled-jobs";
    private final String SCHEDULED_JOBS_TYPE = ".job";

    public MonitorManager(Settings settings) {
        this.client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new TransportAddress(InetAddress.getLoopbackAddress(), 9300));
        this.logger = Loggers.getLogger(getClass());
        this.settings = settings;
    }

    /**
     * Retrieve one monitor.
     *
     * @param policyName The monitor name to retrieve.
     *
     * @return The string representation from the monitor.
     */
    public String getMonitor(@NotNull String policyName, boolean pretty) {
        try {
            logger.log(Level.INFO, String.format(aLocale, "Getting monitor: %s", policyName));
            GetResponse response = client.prepareGet(SCHEDULED_JOBS_INDEX, SCHEDULED_JOBS_TYPE, policyName).get();
            if (response.isExists()) {
                return response.getSourceAsString();
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieve all policies.
     *
     * @return The response string from all policies.
     */
    public String getAllPolicies(boolean pretty, Map<String, String> queryParams) {
        throw new UnsupportedOperationException("Not implemented");

    }

    /**
     * Create a monitor.
     *
     * @param jsonMonitor The monitor string included in the request.
     * @return A string containing the successful creation of an monitor.
     */
    public String createPolicy(String jsonMonitor) {
        try {
            Monitor monitor = Monitor.fromJson(jsonMonitor);
            // Check if a monitor with this name already exists
            String monitorName = monitor.getName();
            if (loadMonitor(monitorName) != null) {
                return String.format(aLocale, "Monitor: %s already exists", monitorName);
            }
            IndexResponse response = client.prepareIndex(SCHEDULED_JOBS_INDEX, SCHEDULED_JOBS_TYPE, monitorName)
                    .setSource(jsonMonitor, XContentType.JSON)
                    .get();
            if (response.status() == RestStatus.CREATED) {
                logger.info("Successfully created monitor: {}.", monitorName);
                return "{\"status\": \"Success\"}";
            } else {
                logger.error("Unable to create monitor: {}.  Return status: {}", monitorName, response.status());
                return "{\"status\": \"Failure\", \"message\": \"" + response.status().toString() + "\"}";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String searchMonitors(String search) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public String acknowledgeMonitor(String monitorID, String actions) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Update a monitor.
     *
     * @param jsonMonitor The monitor string included in the request.
     * @param monitorName The name of the monitor to update.
     *
     * @return A message indicating success or failure of the operation
     * TODO add validation to update!
     */
    public String updatePolicy(String jsonMonitor, String monitorName) throws RuntimeException {
        try {
            if (loadMonitor(monitorName) == null) {
                throw new IllegalArgumentException(String.format(aLocale,
                        "Monitor: %s does not exist and cannot be updated.",
                        monitorName));
            }
            UpdateResponse response = client.prepareUpdate(SCHEDULED_JOBS_INDEX, SCHEDULED_JOBS_TYPE, monitorName)
                    .setDoc(jsonMonitor, XContentType.JSON)
                    .get();
            if (response.status() == RestStatus.OK) {
                logger.info("Successfully updated monitor {}", monitorName);
                return "{\"status\": \"Success\"}";
            } else {
                logger.error("Unable to updates monitor: {}.  Return status: {}", monitorName, response.status());
                return "{\"status\": \"Failure\", \"message\": \"" + response.status().toString() + "\"}";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete an monitor.
     *
     * @param monitorName The name of the monitor to delete.
     *
     * @return a message indicating success or failure
     */
    public String deletePolicy(String monitorName) {
        try {
            DeleteResponse response = client.prepareDelete(SCHEDULED_JOBS_INDEX, SCHEDULED_JOBS_TYPE, monitorName).get();
            if (response.status() == RestStatus.NOT_FOUND) {
                throw new IllegalArgumentException(String.format(aLocale,
                        "Monitor: %s does not exist and cannot be deleted.", monitorName));
            } else {
                return "{\"status\": \"Success\"}";
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Nullable private byte[] loadMonitorSource(String monitorName) {
        logger.log(Level.INFO, String.format(aLocale, "Getting monitor: %s", monitorName));
        GetResponse response = client.prepareGet(SCHEDULED_JOBS_INDEX, SCHEDULED_JOBS_TYPE, monitorName).get();
        if (!response.isExists()) {
            return null;
        } else {
            return response.getSourceAsBytes();
        }
    }

    @Nullable private Monitor loadMonitor(String monitorName) {
        byte[] monitorSource = loadMonitorSource(monitorName);
        if (monitorSource != null) {
            return Monitor.fromJson(monitorSource);
        } else {
            return null;
        }
    }

    /**
     * Perform the input operation of the monitor, then apply the condition on the result.
     * This function performs a privileged operation to turn the search results into a java
     * Map<String, Object> object which will be used as the context for the painless script.
     *
     * @param monitorName the name of the monitor
     *
     * @return The result of the condition applied to the input operation.
     */
    public String testMonitor(String monitorName) {
        Monitor monitor = loadMonitor(monitorName);
        if (monitor == null) {
            logger.error("Monitor {} does not exist, cannot be tested.", monitorName);
            return "{\"Status\" : \"Failure\"}";
        } else {
            return "{\"status\" : \"Not implemented\"}";
        }
    }

    /**
     * Perform the input operation of the monitor.
     *
     * @param monitor The monitor whose input to execute
     * @return The result of the input operation.
     */
    private SearchResponse doSearch(Monitor monitor) throws IOException {
        SearchSourceBuilder searchSource = SearchSourceBuilder.fromXContent(XContentType.JSON.xContent()
                .createParser(NamedXContentRegistry.EMPTY, monitor.getSearch()));
        return client.prepareSearch().setSource(searchSource).get();
    }


    public String doSearchAndPainless(Monitor monitor) {
        throw new UnsupportedOperationException("Not implemented");
//        try {
//            String queryResult = doSearch(policyName);
//            String script = getScript(policyName);
//            SecurityManager sm = System.getSecurityManager();
//            // Special permission needed for using gson due to accessDeclaredMembers security.
//            if (sm != null) {
//                sm.checkPermission(new SpecialPermission());
//            }
//            Map<String, Object> context;
//            context = AccessController.doPrivileged(new PrivilegedAction<Map<String, Object>>() {
//                public Map<String, Object> run() {
//                    return new Gson().fromJson(queryResult, new TypeToken<HashMap<String, Object>>() {}.getType());
//                }
//            });
//            PainlessPolicy painlessPolicy = new PainlessPolicy(policyName, script, context, Collections.emptyMap());
//            result = String.format(aLocale,"Here is the result: \n %s \n", painlessPolicy.getResult());
//        } catch (Exception e) {
//            result = "Failed to perform search and painless with exception: "  + e.getMessage();
//            result += "\n Stack trace: \n";
//            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
//                result += stackTraceElement.toString() + "\n";
//            }
//        }
//        return result;
    }
}
