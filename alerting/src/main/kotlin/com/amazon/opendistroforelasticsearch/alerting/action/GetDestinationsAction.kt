package com.amazon.opendistroforelasticsearch.alerting.action

import org.elasticsearch.action.ActionType

class GetDestinationsAction private constructor() : ActionType<GetDestinationsResponse>(NAME, ::GetDestinationsResponse) {
    companion object {
        val INSTANCE = GetDestinationsAction()
        val NAME = "cluster:admin/alerting/destination/get"
    }
}
