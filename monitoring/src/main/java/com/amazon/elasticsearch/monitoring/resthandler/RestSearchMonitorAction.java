/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 */

package com.amazon.elasticsearch.monitoring.resthandler;

import com.amazon.elasticsearch.monitoring.MonitoringPlugin;
import com.amazon.elasticsearch.model.ScheduledJob;
import com.amazon.elasticsearch.monitoring.model.Monitor;
import com.amazon.elasticsearch.monitoring.util.RestHandlerUtilsKt;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestResponseListener;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOBS_INDEX;
import static com.amazon.elasticsearch.model.ScheduledJob.SCHEDULED_JOB_TYPE;
import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.rest.RestRequest.Method.GET;

/**
 * Rest handlers to search for monitors.
 */
public class RestSearchMonitorAction extends BaseRestHandler {

    public RestSearchMonitorAction(final Settings settings, final RestController controller) {
        super(settings);
        // Search for monitors
        controller.registerHandler(GET, MonitoringPlugin.MONITOR_BASE_URI + "_search", this);
    }

    @Override
    public String getName() {
        return "search_monitor_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.parseXContent(request.contentOrSourceParamParser());
        searchSourceBuilder.fetchSource(RestHandlerUtilsKt.context(request));
        // We add a term query ontop of the customer query to ensure that only scheduled jobs of monitor type are
        // searched.
        searchSourceBuilder.query(QueryBuilders.boolQuery().must(searchSourceBuilder.query())
                .filter(QueryBuilders.termQuery(Monitor.MONITOR_TYPE + ".type", Monitor.MONITOR_TYPE)));
        SearchRequest searchRequest = new SearchRequest()
                .source(searchSourceBuilder)
                .indices(SCHEDULED_JOBS_INDEX)
                .types(SCHEDULED_JOB_TYPE);
        return channel -> client.search(searchRequest, searchMonitorResponse(channel));
    }

    private static RestResponseListener<SearchResponse> searchMonitorResponse(RestChannel channel) {
        return new RestResponseListener<SearchResponse>(channel) {
            @Override
            public RestResponse buildResponse(final SearchResponse response) throws Exception {
                if (response.isTimedOut()) {
                    return new BytesRestResponse(RestStatus.REQUEST_TIMEOUT, response.toString());
                }
                for (SearchHit hit : response.getHits()) {
                    try (XContentParser hitsParser =  XContentType.JSON.xContent()
                            .createParser(channel.request().getXContentRegistry(), hit.getSourceAsString())) {
                        ScheduledJob monitor = ScheduledJob.Companion.parse(hitsParser, hit.getId(), hit.getVersion());
                        hit.sourceRef(monitor.toXContent(jsonBuilder(), EMPTY_PARAMS).bytes());
                    }
                }
                return new BytesRestResponse(RestStatus.OK, response.toXContent(channel.newBuilder(), EMPTY_PARAMS));
            }
        };
    }
}
