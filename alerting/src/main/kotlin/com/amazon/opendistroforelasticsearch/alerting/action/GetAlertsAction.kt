package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.ActionType

class GetAlertsAction private constructor() : ActionType<GetAlertsResponse>(NAME, ::GetAlertsResponse) {
    companion object {
        val INSTANCE = GetAlertsAction()
        val NAME = "cluster:admin/opendistro/alerting/alerts/get"
    }
}
