/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.monitoring.resthandler;

import com.amazon.elasticsearch.monitoring.MonitoringPlugin;
import com.amazon.elasticsearch.monitoring.model.Monitor;
import com.amazon.elasticsearch.monitoring.util.RestHandlerUtilsKt;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestActions;
import org.elasticsearch.rest.action.RestResponseListener;

import java.io.IOException;

import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOBS_INDEX;
import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOB_TYPE;
import static org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.PUT;

/**
 * Rest handlers to create and update monitors
 */
public class RestIndexMonitorAction extends BaseRestHandler {

    private static final String REFRESH = "refresh";

    public RestIndexMonitorAction(final Settings settings, final RestController controller) {
        super(settings);
        controller.registerHandler(POST, MonitoringPlugin.MONITOR_BASE_URI, this); // Create a new monitor
        controller.registerHandler(PUT, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this);
    }

    @Override
    public String getName() {
        return "index_monitor_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final String id = request.param("monitorID", Monitor.NO_ID);
        if (request.method() == PUT && Monitor.NO_ID.equals(id)) {
            throw new IllegalArgumentException("Missing monitor ID");
        }

        // Validate request by parsing JSON to Monitor
        XContentParser xcp = request.contentParser();
        ensureExpectedToken(Token.START_OBJECT, xcp.nextToken(), xcp::getTokenLocation);
        Monitor monitor = Monitor.parse(xcp, id);
        XContentBuilder builder = XContentFactory.contentBuilder(request.getXContentType());
        final IndexRequest indexRequest = new IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
                .source(monitor.toXContentWithType(builder));
        if (request.method() == PUT) {
            indexRequest.id(id).version(RestActions.parseVersion(request));
        }
        if (request.hasParam(REFRESH)) {
            indexRequest.setRefreshPolicy(request.param(REFRESH));
        }
        return channel -> client.index(indexRequest, indexMonitorResponse(channel));
    }

    private static RestResponseListener<IndexResponse> indexMonitorResponse(RestChannel channel) {
        return new RestResponseListener<IndexResponse>(channel) {
            @Override
            public RestResponse buildResponse(IndexResponse response) throws Exception {
                if (response.getShardInfo().getSuccessful() < 1) {
                    // TODO: Handle failed indexing
                }

                XContentBuilder builder = channel.newBuilder()
                        .startObject()
                        .field(RestHandlerUtilsKt._ID, response.getId())
                        .field(RestHandlerUtilsKt._VERSION, response.getVersion())
                        .endObject();

                RestResponse restResponse = new BytesRestResponse(response.status(), builder);
                if (response.status() == RestStatus.CREATED) {
                    String location = MonitoringPlugin.MONITOR_BASE_URI + response.getId();
                    restResponse.addHeader("Location", location);
                }
                return restResponse;
            }
        };
    }
}
