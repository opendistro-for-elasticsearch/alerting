package com.amazon.opendistroforelasticsearch.alerting.transport

import com.amazon.opendistroforelasticsearch.alerting.MonitorRunner
import com.amazon.opendistroforelasticsearch.alerting.action.ExecuteMonitorAction
import com.amazon.opendistroforelasticsearch.alerting.action.ExecuteMonitorRequest
import com.amazon.opendistroforelasticsearch.alerting.action.ExecuteMonitorResponse
import com.amazon.opendistroforelasticsearch.alerting.core.model.ScheduledJob
import com.amazon.opendistroforelasticsearch.alerting.model.Monitor
import com.amazon.opendistroforelasticsearch.alerting.util.AlertingException
import com.amazon.opendistroforelasticsearch.commons.ConfigConstants
import com.amazon.opendistroforelasticsearch.commons.authuser.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.support.ActionFilters
import org.elasticsearch.action.support.HandledTransportAction
import org.elasticsearch.client.Client
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler
import org.elasticsearch.common.xcontent.NamedXContentRegistry
import org.elasticsearch.common.xcontent.XContentHelper
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.tasks.Task
import org.elasticsearch.transport.TransportService
import java.time.Instant

private val log = LogManager.getLogger(TransportGetMonitorAction::class.java)

class TransportExecuteMonitorAction @Inject constructor(
    transportService: TransportService,
    private val client: Client,
    private val runner: MonitorRunner,
    actionFilters: ActionFilters,
    val xContentRegistry: NamedXContentRegistry
) : HandledTransportAction<ExecuteMonitorRequest, ExecuteMonitorResponse> (
        ExecuteMonitorAction.NAME, transportService, actionFilters, ::ExecuteMonitorRequest) {

    override fun doExecute(task: Task, execMonitorRequest: ExecuteMonitorRequest, actionListener: ActionListener<ExecuteMonitorResponse>) {

        val userStr = client.threadPool().threadContext.getTransient<String>(ConfigConstants.OPENDISTRO_SECURITY_USER_INFO_THREAD_CONTEXT)
        log.debug("User and roles string from thread context: $userStr")
        val user: User? = User.parse(userStr)

        client.threadPool().threadContext.stashContext().use {
            val executeMonitor = fun(monitor: Monitor) {
                // Launch the coroutine with the clients threadContext. This is needed to preserve authentication information
                // stored on the threadContext set by the security plugin when using the Alerting plugin with the Security plugin.
                // runner.launch(ElasticThreadContextElement(client.threadPool().threadContext)) {
                runner.launch {
                    val (periodStart, periodEnd) =
                            monitor.schedule.getPeriodEndingAt(Instant.ofEpochMilli(execMonitorRequest.requestEnd.millis))
                    try {
                        val monitorRunResult = runner.runMonitor(monitor, periodStart, periodEnd, execMonitorRequest.dryrun)
                        withContext(Dispatchers.IO) {
                            actionListener.onResponse(ExecuteMonitorResponse(monitorRunResult))
                        }
                    } catch (e: Exception) {
                        log.error("Unexpected error running monitor", e)
                        withContext(Dispatchers.IO) {
                            actionListener.onFailure(AlertingException.wrap(e))
                        }
                    }
                }
            }

            if (execMonitorRequest.monitorId != null) {
                val getRequest = GetRequest(ScheduledJob.SCHEDULED_JOBS_INDEX).id(execMonitorRequest.monitorId)
                client.get(getRequest, object : ActionListener<GetResponse> {
                    override fun onResponse(response: GetResponse) {
                        if (!response.isExists) {
                            actionListener.onFailure(AlertingException.wrap(
                                    ElasticsearchStatusException("Can't find monitor with id: ${response.id}", RestStatus.NOT_FOUND)
                            ))
                            return
                        }
                        if (!response.isSourceEmpty) {
                            XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE,
                                    response.sourceAsBytesRef, XContentType.JSON).use { xcp ->
                                val monitor = ScheduledJob.parse(xcp, response.id, response.version) as Monitor
                                executeMonitor(monitor)
                            }
                        }
                    }

                    override fun onFailure(t: Exception) {
                        actionListener.onFailure(AlertingException.wrap(t))
                    }
                })
            } else {
                val monitor = when (user?.name.isNullOrEmpty()) {
                    true -> execMonitorRequest.monitor as Monitor
                    false -> (execMonitorRequest.monitor as Monitor).copy(user = user)
                }
                executeMonitor(monitor)
            }
        }
    }
}
