/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler;

import com.amazon.elasticsearch.MonitoringPlugin;
import com.amazon.elasticsearch.model.ScheduledJob;
import com.amazon.elasticsearch.util.RestHandlerUtilsKt;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
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
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;

import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOBS_INDEX;
import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOB_TYPE;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.HEAD;

/**
 * This class consists of the REST handler to retrieve a monitor .
 */
public class RestGetMonitorAction extends BaseRestHandler {

    public RestGetMonitorAction(final Settings settings, final RestController controller) {
        super(settings);
        // Get a specific monitor
        controller.registerHandler(GET, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this);
        controller.registerHandler(HEAD, MonitoringPlugin.MONITOR_BASE_URI + "{monitorID}", this);
    }


    @Override
    public String getName() {
        return "get_monitor_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) {
        String monitorId = request.param("monitorID");
        if (monitorId == null || monitorId.isEmpty()) {
            throw new IllegalArgumentException("missing id");
        }
        GetRequest getRequest =
                new GetRequest(SCHEDULED_JOBS_INDEX, SCHEDULED_JOB_TYPE, monitorId)
                        .version(RestActions.parseVersion(request))
                        .fetchSourceContext(RestHandlerUtilsKt.context(request));
        if (request.method() == HEAD) {
            getRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE);
        }
        return channel -> client.get(getRequest, getMonitorResponse(channel));
    }

    private static RestResponseListener<GetResponse> getMonitorResponse(RestChannel channel) {
        return new RestResponseListener<GetResponse>(channel) {
            @Override
            public RestResponse buildResponse(GetResponse response) throws Exception {
                if (!response.isExists()) {
                    return new BytesRestResponse(RestStatus.NOT_FOUND, channel.newBuilder());
                }

                XContentBuilder builder = channel.newBuilder()
                        .startObject()
                        .field(RestHandlerUtilsKt._ID, response.getId())
                        .field(RestHandlerUtilsKt._VERSION, response.getVersion());
                if (!response.isSourceEmpty()) {
                    XContentParser xcp = XContentType.JSON.xContent()
                            .createParser(channel.request().getXContentRegistry(), response.getSourceAsBytesRef());
                    ScheduledJob monitor = ScheduledJob.Companion.parse(xcp, response.getId(), response.getVersion());
                    builder.field("monitor", monitor);
                }
                builder.endObject();
                return new BytesRestResponse(RestStatus.OK, builder);
            }
        };
    }
}
