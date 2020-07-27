package com.amazon.opendistroforelasticsearch.alerting.resthandler

import com.amazon.opendistroforelasticsearch.alerting.AlertingPlugin
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob.Companion.SCHEDULED_JOBS_INDEX
import com.amazon.opendistroforelasticsearch.alerting.util.REFRESH
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.BaseRestHandler
import org.elasticsearch.rest.RestHandler.Route
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.action.RestStatusToXContentListener
import java.io.IOException

/**
 * Rest handler to delete EmailAccount.
 */
class RestDeleteEmailGroupAction : BaseRestHandler() {

    override fun getName(): String {
        return "delete_email_group_action"
    }

    override fun routes(): List<Route> {
        return listOf(
                Route(RestRequest.Method.DELETE, "${AlertingPlugin.EMAIL_GROUP_BASE_URI}/{emailGroupID}")
        )
    }

    @Throws(IOException::class)
    override fun prepareRequest(request: RestRequest, client: NodeClient): RestChannelConsumer {
        val emailGroupID = request.param("emailGroupID")
        val refreshPolicy = WriteRequest.RefreshPolicy.parse(request.param(REFRESH, WriteRequest.RefreshPolicy.IMMEDIATE.value))

        return RestChannelConsumer { channel ->
            val deleteEmailGroupRequest = DeleteRequest(SCHEDULED_JOBS_INDEX, emailGroupID)
                    .setRefreshPolicy(refreshPolicy)
            client.delete(deleteEmailGroupRequest, RestStatusToXContentListener(channel))
        }
    }
}
