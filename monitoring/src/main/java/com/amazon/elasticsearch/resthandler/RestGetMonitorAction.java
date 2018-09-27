/*
 * Copyright 2013-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */
package com.amazon.elasticsearch.resthandler;

import com.amazon.elasticsearch.MonitoringPlugin;
import com.amazon.elasticsearch.model.ScheduledJob;
import com.amazon.elasticsearch.monitor.Monitor;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
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

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.HEAD;

/**
 * This class consists of the REST handler to retrieve a monitor .
 */
public class RestGetMonitorAction extends BaseRestHandler {

    public RestGetMonitorAction(final Settings settings, final RestController controller) {
        super(settings);
        // Get a specific monitor
        controller.registerHandler(GET, "/_awses/monitors/{monitorID}", this);
        controller.registerHandler(HEAD, "_awses/monitors/{monitorID}", this);
    }


    @Override
    public String getName() {
        return "Get one monitor.";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        String monitorId = request.param("monitorID");
        if (monitorId == null || monitorId.isEmpty()) {
            throw new IllegalArgumentException("missing id");
        }
        GetRequest getRequest =
                new GetRequest(ScheduledJob.SCHEDULED_JOBS_INDEX, ScheduledJob.SCHEDULED_JOB_TYPE, monitorId)
                        .version(RestActions.parseVersion(request))
                        .fetchSourceContext(context(request));
        if (request.method() == HEAD) {
            getRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE);
        }
        return channel -> client.get(getRequest, getMonitorResponse(channel));
    }

    private static RestResponseListener<GetResponse> getMonitorResponse(RestChannel channel) {
        return new RestResponseListener<GetResponse>(channel) {
            @Override
            public RestResponse buildResponse(GetResponse getResponse) throws Exception {
                if (!getResponse.isExists()) {
                    return new BytesRestResponse(RestStatus.NOT_FOUND, channel.newBuilder());
                }

                XContentBuilder builder = channel.newBuilder()
                        .startObject()
                        .field("_id", getResponse.getId())
                        .field("_version", getResponse.getVersion());
                if (!getResponse.isSourceEmpty()) {
                    XContentParser xcp = XContentType.JSON.xContent()
                            .createParser(channel.request().getXContentRegistry(), getResponse.getSourceAsBytesRef());
                    ScheduledJob monitor = ScheduledJob.Companion.parse(xcp, getResponse.getId(), getResponse.getVersion());
                    builder.field("monitor", monitor);
                }
                builder.endObject();
                return new BytesRestResponse(RestStatus.OK, builder);
            }
        };
    }

    /**
     * Checks to see if the request came from Kibana, if so we want to return the UI Metadata from the document.
     * If the request came from the client then we exclude the UI Metadata from the search result.
     *
     * @param request
     * @return FetchSourceContext
     */
    private FetchSourceContext context(final RestRequest request) {
        String userAgent = Strings.coalesceToEmpty(request.header("User-Agent"));
        if (!userAgent.contains(MonitoringPlugin.KIBANA_USER_AGENT)) {
            return new FetchSourceContext(true, Strings.EMPTY_ARRAY, MonitoringPlugin.UI_METADATA_EXCLUDE);
        }
        return null;
    }
}
