/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler;

import com.amazon.elasticsearch.monitor.Monitor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
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
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.PUT;

/**
 * Rest handlers to create and update monitors
 */
public class RestIndexMonitorAction extends BaseRestHandler {

    public RestIndexMonitorAction(final Settings settings, final RestController controller) {
        super(settings);
        controller.registerHandler(POST, "/_awses/monitors", this); // Create a new monitor
        controller.registerHandler(PUT, "/_awses/monitors/{monitorID}", this);
    }

    @Override
    public String getName() {
        return "Create a monitor.";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final String id = request.param("monitorID", Monitor.NO_ID);
        if (request.method() == PUT && Monitor.NO_ID.equals(id)) {
            throw new IllegalArgumentException("Missing monitor ID");
        }

        // Validate request by parsing JSON to Monitor
        Monitor monitor = Monitor.fromJson(request.contentParser(), id);
        final IndexRequest indexRequest = new IndexRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE)
                .source(request.content(), XContentType.JSON);
        if (request.method() == PUT) {
            indexRequest.id(id).version(RestActions.parseVersion(request));
        }
        return channel -> client.index(indexRequest, indexMonitorResponse(channel));
    }

    private static RestResponseListener<IndexResponse> indexMonitorResponse(RestChannel channel) {
        return new RestResponseListener<IndexResponse>(channel) {
            @Override
            public RestResponse buildResponse(IndexResponse indexResponse) throws Exception {
                if (indexResponse.getShardInfo().getSuccessful() < 1) {
                    // TODO: Handle failed indexing
                }

                XContentBuilder builder = channel.newBuilder()
                        .startObject()
                        .field("_id", indexResponse.getId())
                        .field("_version", indexResponse.getVersion())
                        .endObject();

                RestResponse restResponse = new BytesRestResponse(indexResponse.status(), builder);
                if (indexResponse.status() == RestStatus.CREATED) {
                    String location = "/_awses/monitors/" + indexResponse.getId();
                    restResponse.addHeader("Location", location);
                }
                return restResponse;
            }
        };
    }
}
