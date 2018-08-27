/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler;

import com.amazon.elasticsearch.MonitoringPlugin;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.junit.annotations.TestLogging;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import static java.util.Arrays.asList;

@TestLogging("level:DEBUG")
@ESIntegTestCase.ClusterScope(scope=ESIntegTestCase.Scope.SUITE, numDataNodes=3)
public class RestDeleteMonitorTests extends ESIntegTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return asList(MonitoringPlugin.class);
    }

    @Test
    public void testPluginIsLoaded() {
        NodesInfoResponse response = client().admin().cluster().prepareNodesInfo().setPlugins(true).get();
        for (NodeInfo nodeInfo : response.getNodes()) {
            boolean pluginFound = false;
            for (PluginInfo pluginInfo : nodeInfo.getPlugins().getPluginInfos()) {
                if (pluginInfo.getName().equals(MonitoringPlugin.class.getName())) {
                    pluginFound = true;
                    break;
                }
            }
            assertTrue(pluginFound);
        }
        ensureYellow();
    }

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put(NetworkModule.HTTP_ENABLED.getKey(), "true")
                .put(NetworkModule.HTTP_ENABLED.getKey(), "true")
                .build();
    }

    @Test
    public void testGetAlertEmpty() throws Exception {
        Settings settings = Settings.builder().put("client.transport.ignore_cluster_name",true).build();
        Client client = client();
//        Response response = getRestClient().performRequest("GET",
//                "/_awses/policies",
//                Collections.emptyMap());
//        GetRequest request = new GetRequest();
//        client().get(request);
        //getRestClient();
        //this.restClient = getRestClient();
        //getRestClient();
        //MonitorManager policyManager = new MonitorManager(getRestClient(), logger);
        //this.restClient = RestClient.builder(new HttpHost("localhost", 55716, "http")).build();

        //Response response;
        //response = restClient.performRequest(RestRequest.Method.GET.toString(), POLICY_INDEX_PATH+ "_search");
    }

    protected static TransportAddress getTransportAddress() throws UnknownHostException {
        String host = System.getenv("ES_TEST_HOST");
        String port = System.getenv("ES_TEST_PORT");

        if(host == null) {
            host = "localhost";
            System.out.println("ES_TEST_HOST enviroment variable does not exist. choose default 'localhost'");
        }

        if(port == null) {
            port = "9300";
            System.out.println("ES_TEST_PORT enviroment variable does not exist. choose default '9300'");
        }

        System.out.println(String.format("Connection details: host: %s. port:%s.", host, port));
        return new TransportAddress(InetAddress.getByName(host), Integer.parseInt(port));
    }

}
