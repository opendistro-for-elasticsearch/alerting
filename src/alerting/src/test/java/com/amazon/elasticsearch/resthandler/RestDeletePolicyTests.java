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
package com.amazon.elasticsearch.resthandler;

import com.amazon.elasticsearch.policy.PolicyManager;
import com.amazon.elasticsearch.policy.PolicyPlugin;
import junit.framework.TestCase;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.junit.annotations.TestLogging;
import org.elasticsearch.transport.Netty4Plugin;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;

@TestLogging("level:DEBUG")
@ESIntegTestCase.ClusterScope(scope=ESIntegTestCase.Scope.SUITE, numDataNodes=3)
public class RestDeletePolicyTests extends ESIntegTestCase {

//    private final String POLICY_INDEX_PATH = "/.scheduled-jobs/policies/";
//    private RestClient restClient;
//    private Client client;
//    //private PolicyManager policyManager = new PolicyManager(Settings.EMPTY);
//
//    public RestDeletePolicyTests() {
//        //logger.log(Level.ERROR, "Created testdeletepolicyclass");
//        //this.restClient = RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
//    }
//
//    @Before
//    public void setUp() throws Exception {
//        super.setUp();
//        //this.client = client();
//        //this.restClient = (RestClient) client();
//        //this.restClient = getRestClient();
//    }
//
//    @Override
//    protected Collection<Class<? extends Plugin>> nodePlugins() {
//        return asList(PolicyPlugin.class, Netty4Plugin.class);
//    }
//
//    @Test
//    public void testPluginIsLoaded() {
//        NodesInfoResponse response = client().admin().cluster().prepareNodesInfo().setPlugins(true).get();
//        for (NodeInfo nodeInfo : response.getNodes()) {
//            boolean pluginFound = false;
//            for (PluginInfo pluginInfo : nodeInfo.getPlugins().getPluginInfos()) {
//                if (pluginInfo.getName().equals(PolicyPlugin.class.getName())) {
//                    pluginFound = true;
//                    break;
//                }
//            }
//            assertTrue(pluginFound);
//        }
//        ensureYellow();
//    }
//
//    @Override
//    protected Settings nodeSettings(int nodeOrdinal) {
//        return Settings.builder()
//                .put(super.nodeSettings(nodeOrdinal))
//                .put(NetworkModule.HTTP_ENABLED.getKey(), "true")
//                .put(NetworkModule.HTTP_ENABLED.getKey(), "true")
//                .build();
//    }
//
//    @Test
//    public void testGetAlertEmpty() throws Exception {
//        Settings settings = Settings.builder().put("client.transport.ignore_cluster_name",true).build();
//        TransportClient client = new PreBuiltTransportClient(settings).addTransportAddress(getTransportAddress());
////        Response response = getRestClient().performRequest("GET",
////                "/_awses/policies",
////                Collections.emptyMap());
////        GetRequest request = new GetRequest();
////        client().get(request);
//        //getRestClient();
//        //this.restClient = getRestClient();
//        //getRestClient();
//        //PolicyManager policyManager = new PolicyManager(getRestClient(), logger);
//        //this.restClient = RestClient.builder(new HttpHost("localhost", 55716, "http")).build();
//
//        //Response response;
//        //response = restClient.performRequest(RestRequest.Method.GET.toString(), POLICY_INDEX_PATH+ "_search");
//    }
//
//    protected static TransportAddress getTransportAddress() throws UnknownHostException {
//        String host = System.getenv("ES_TEST_HOST");
//        String port = System.getenv("ES_TEST_PORT");
//
//        if(host == null) {
//            host = "localhost";
//            System.out.println("ES_TEST_HOST enviroment variable does not exist. choose default 'localhost'");
//        }
//
//        if(port == null) {
//            port = "9300";
//            System.out.println("ES_TEST_PORT enviroment variable does not exist. choose default '9300'");
//        }
//
//        System.out.println(String.format("Connection details: host: %s. port:%s.", host, port));
//        return new TransportAddress(InetAddress.getByName(host), Integer.parseInt(port));
//    }

}
