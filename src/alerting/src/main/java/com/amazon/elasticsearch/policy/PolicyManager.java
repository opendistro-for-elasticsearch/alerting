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
package com.amazon.elasticsearch.policy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.logging.ServerLoggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * The policy manager currently contains all the business logic for creating, deleting, retreiving, and running the policies
 * and policies.
 */
public class PolicyManager {

    private RestClient restClient;
    private Logger logger;
    private Settings settings;
    private Locale aLocale = new Locale("en", "US");

    private final String POLICY_INDEX_PATH = "/.scheduled-jobs/policies/";
    private final String UPDATE_PATH = "/_update";
    private final String SEARCH_PATH = "/_update";
    private final String SOURCE = "_source";
    private final String HITS = "hits";

    public PolicyManager(Settings settings) {
        this.restClient = RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
        this.logger = ServerLoggers.getLogger(getClass(), settings);
        this.settings = settings;
    }

    /**
     * Retrieve one policy.
     *
     * @param policyName The policy name to retrieve.
     * @return The response string from the policy.
     * @throws RuntimeException
     */
    public String getPolicy(String policyName, boolean pretty) throws RuntimeException {
        Response response;
        if (!policyExists(policyName)) {
            logger.log(Level.INFO, String.format(aLocale, "Policy: %s does not exist!", policyName));
            throw new IllegalArgumentException(String.format(aLocale, "Policy: %s does not exist!", policyName));
        } else {
            try {
                response = restClient.performRequest(RestRequest.Method.GET.toString(), POLICY_INDEX_PATH+ policyName);
                logger.log(Level.INFO, String.format(aLocale, "Getting policy: %s", policyName));
                JSONObject queryResults = new JSONObject(EntityUtils.toString(response.getEntity()));
                Policy policy = new Policy(settings, queryResults.getJSONObject(SOURCE).toString(pretty? 4: 0));
                return policy.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Retrieve all policies.
     * @return The response string from all policies.
     * @throws RuntimeException
     */
    public String getAllPolicies(boolean pretty, Map<String, String> queryParams) throws RuntimeException {
        String result = "";
        Response response;
        try {
            response = restClient.performRequest(RestRequest.Method.GET.toString(), POLICY_INDEX_PATH+ "_search", queryParams);
            JSONObject queryResult = new JSONObject(EntityUtils.toString(response.getEntity()));
            JSONArray policyArray = queryResult.getJSONObject(HITS).getJSONArray(HITS);
            List<Policy> policies = new ArrayList<Policy>();
            for (int i = 0; i < policyArray.length(); i++) {
                policies.add(new Policy(settings, policyArray.getJSONObject(i).getJSONObject(SOURCE).toString(pretty? 4: 0)));
            }
            result = policies.toString();
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                throw new IllegalArgumentException("No polices found");
            } else {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * Create a policy.
     *
     * @param monitorInfo The policy string included in the request.
     * @return A string containing the successful creation of an policy.
     * @throws RuntimeException
     */
    public String createPolicy(String monitorInfo) throws RuntimeException {
        String result = "";
        try {
            JSONObject monitor = new JSONObject(monitorInfo);
            String policyName = monitor.getString("name");
            validatePolicy(monitor);
            // Check if a monitor with this name already exists
            if (policyExists(policyName)) {
                return String.format(aLocale, "Policy: %s already exists", policyName);
            }
            HttpEntity entity = new NStringEntity(monitorInfo, ContentType.APPLICATION_JSON);
            restClient.performRequest(RestRequest.Method.POST.toString(), POLICY_INDEX_PATH + policyName, Collections.emptyMap(), entity);
            logger.log(Level.INFO, String.format(aLocale, "Created policy: %s", policyName));
            result = String.format(aLocale,"Successfully created policy: %s.", policyName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Your policy creation request is invalid");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public String searchMonitors(String search) {
        String result = "";
        return result;
    }

    public String acknowledgeMonitor(String monitorID, String actions) {
        String result = "";
        return result;
    }

    /**
     * Update a policy.
     *
     * @param policyInfo The policy string included in the request.
     * @param policyName The name of the policy to update.
     * @return A string containing the success of updating the policy.
     * @throws RuntimeException
     * TODO add validation to update!
     */
    public String updatePolicy(String policyInfo, String policyName) throws RuntimeException {
        String result = "";
        try {
            if (!policyExists(policyName)) {
                throw new IllegalArgumentException(String.format(aLocale,
                        "Monitor: %s does not exist and cannot be updated.",
                        policyName));
            }
            HttpEntity entity = new NStringEntity(policyInfo, ContentType.APPLICATION_JSON);
            restClient.performRequest(RestRequest.Method.POST.toString(),
                    POLICY_INDEX_PATH + policyName + UPDATE_PATH,
                    Collections.emptyMap(),
                    entity);
            result = String.format(aLocale, "Successfully updated policy: %s.", policyName);
            logger.log(Level.INFO, String.format(aLocale, "Updated policy: %s with the following: %s", policyName, policyInfo));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * Delete an policy.
     *
     * @param policyName The name of the policy to delete.
     * @return
     * @throws RuntimeException
     */
    public String deletePolicy(String policyName) throws RuntimeException {
        try {
            restClient.performRequest("DELETE", POLICY_INDEX_PATH + policyName);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return "Successfully deleted policy: " + policyName;
    }

    /**
     * Perform the input operation of the policy.
     *
     * @param policyName The name of the policy to run the input of.
     * @return The result of the input operation.
     */
    public String doSearch(String policyName) {
        String result = "";
        try {
            if (!policyExists(policyName)) {
                throw new Exception(String.format(aLocale,"Policy: %s does not exist!", policyName));
            }
            Response response = restClient.performRequest(RestRequest.Method.GET.toString(),
                    SEARCH_PATH,
                    Collections.emptyMap(),
                    new NStringEntity(getQuery(policyName),
                            ContentType.APPLICATION_JSON));
            result = EntityUtils.toString(response.getEntity());
        } catch (Exception e) {
            result = "Failed to run the search with exception: " + e.getMessage();
        }
        return result;
    }

    /**
     * Perform the input operation of the policy, then apply the condition on the result.
     * This function performs a privileged operation to turn the search results into a java
     * Map<String, Object> object which will be used as the context for the painless script.
     *
     * @param policyName
     * @return The result of the condition applied to the input operation.
     */
    public String doSearchAndPainless(String policyName) {
        String result = "";
        try {
            String queryResult = doSearch(policyName);
            String script = getScript(policyName);
            SecurityManager sm = System.getSecurityManager();
            // Special permission needed for using gson due to accessDeclaredMembers security.
            if (sm != null) {
                sm.checkPermission(new SpecialPermission());
            }
            Map<String, Object> context;
            context = AccessController.doPrivileged(new PrivilegedAction<Map<String, Object>>() {
                public Map<String, Object> run() {
                    return new Gson().fromJson(queryResult, new TypeToken<HashMap<String, Object>>() {}.getType());
                }
            });
            PainlessPolicy painlessPolicy = new PainlessPolicy(policyName, script, context, Collections.emptyMap());
            result = String.format(aLocale,"Here is the result: \n %s \n", painlessPolicy.getResult());
        } catch (Exception e) {
            result = "Failed to perform search and painless with exception: "  + e.getMessage();
            result += "\n Stack trace: \n";
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                result += stackTraceElement.toString() + "\n";
            }
        }
        return result;
    }

    /**
     * Validate that the policy is in a structured format that is expected. If the policy is not in proper format we throw
     * a illegal argument exception.
     *
     * @param policy
     * @throws IllegalArgumentException
     */
    private void validatePolicy(JSONObject policy) throws IllegalArgumentException {
        if (!policy.has("name"))  {
            throw new IllegalArgumentException("Policy must have a name");
        }
        if (!policy.has("enabled")) {
            throw new IllegalArgumentException("Policy must contain enabled -> True / False");
        }
        if (!policy.has("schedule")) {
            throw new IllegalArgumentException("Policy must contain a schedule");
        }
        if (!policy.has("search")) {
            throw new IllegalArgumentException("Policy must contain a search");
        }
    }

    /**
     * Check if an policy exists. This is done by querying the document using the HEAD method. If the document exists the
     * operation returns 200, if it does not the response code will be 404.
     *
     * @param policyName The name of the policy to check existence of.
     * @return boolean True if the policy exists, false otherwise.
     * @throws IllegalStateException
     */
    public boolean policyExists(String policyName) throws IllegalStateException {
        // Check if the index exists, if not create it.
        try {
            Response response = this.restClient.performRequest(RestRequest.Method.HEAD.toString(), POLICY_INDEX_PATH + policyName);
            int responseCode = response.getStatusLine().getStatusCode();
            return responseCode == 200;
        } catch (Exception e) {
            throw new IllegalStateException("Check if policy exists failed with exception: " + e.getMessage());
        }
    }

    /**
     * Get the condition of a particular policy. This is done by querying the index and parsing the response of the document.
     *
     * @param policyName The name of the policy to get the condition of.
     * @return The string representing the condition of the associated policy.
     * @throws Exception
     */
    public String getScript(String policyName) throws Exception {
        try {
            JSONObject policy = new JSONObject(this.getPolicy(policyName, false));
            JSONObject source = policy.getJSONObject("_source");
            return source.getJSONArray("actions").getJSONObject(0)
                .getJSONObject("condition")
                .getJSONObject("script")
                .getString("source");
        } catch (Exception e) {
            throw new Exception("Getting query failed with exception: " + e.getMessage());
        }
    }

    /**
     * Get the input of a particular policy. This is done by querying the index and parsing the response of the document.
     *
     * @param policyName The name of the policy to get the input of.
     * @return The string representing the input.
     * @throws Exception
     */
    public String getQuery(String policyName) throws Exception {
        try {
            JSONObject policy = new JSONObject(this.getPolicy(policyName, false));
            JSONObject source = policy.getJSONObject("_source");
            return source.getJSONObject("search").toString();
        } catch (Exception e) {
            throw new Exception("Getting query failed with exception: " + e.getMessage());
        }
    }
}
