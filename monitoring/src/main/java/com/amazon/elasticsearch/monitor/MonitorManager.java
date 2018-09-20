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

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

/**
 * The monitor manager currently contains all the business logic for creating, deleting, retreiving, and running the policies
 * and policies.
 */
public class MonitorManager {

    private MonitorManager() {
    }

    /**
     * Perform the input operation of the monitor.
     *
     * @param monitor The monitor whose input to execute
     * @return The result of the input operation.
     */
    private static SearchResponse doSearch(Client client, Monitor monitor) throws IOException {
        SearchSourceBuilder searchSource = SearchSourceBuilder.fromXContent(XContentType.JSON.xContent()
                .createParser(NamedXContentRegistry.EMPTY, monitor.getInputs().toString()));
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
//                    return new Gson().parse(queryResult, new TypeToken<HashMap<String, Object>>() {}.getType());
//                }
//            });
//            PainlessPolicy painlessPolicy = new PainlessPolicy(policyName, script, context, Collections.emptyMap());
//            result = String.format(aLocale,"Here is the result: \n %s \n", painlessPolicy.getResult());
//        } catch (Exception e) {
//            result = "Failed to perform source and painless with exception: "  + e.getMessage();
//            result += "\n Stack trace: \n";
//            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
//                result += stackTraceElement.toString() + "\n";
//            }
//        }
//        return result;
    }
}
