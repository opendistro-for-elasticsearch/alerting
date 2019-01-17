package com.amazon.elasticsearch.monitoring.util

enum class DestinationType(val type: String) {
    CHIME("chime"),
    SLACK("slack"),
    CUSTOM_WEBHOOK("custom_webhook")
}