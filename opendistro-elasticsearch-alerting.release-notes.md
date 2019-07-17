## Version 1.1.0 (Current)

### New Features
  * Adds support for Elasticsearch 7.1.1 - [PR #74](https://github.com/opendistro-for-elasticsearch/alerting/pull/74)
  * Return stack trace for errors in Groovy/Painless script in UI when creating a trigger - [PR #72](https://github.com/opendistro-for-elasticsearch/alerting/pull/72)

## 2019-06-25, Version 1.0.0

### New Features
  * Adds support for Elasticsearch 7.0.1 - [PR #41](https://github.com/opendistro-for-elasticsearch/alerting/pull/41)
  * Adds support for throttling on actions - [PR #48](https://github.com/opendistro-for-elasticsearch/alerting/pull/48)

### Bug fixes
  * Validate all 2XX status code in Webhook response  - [PR #50](https://github.com/opendistro-for-elasticsearch/alerting/pull/50)
  * Allows encoding while constructing HTTP request for sending notification - [PR #35](https://github.com/opendistro-for-elasticsearch/alerting/pull/35)
  * Convert AlertMover/MonitorRunner to coroutines - [PR #11](https://github.com/opendistro-for-elasticsearch/alerting/pull/11)

## 2019-04-24, Version 0.9.0

### New Features
  * Adds support for Elasticsearch 6.7.1 - #19
  * Add http proxy support to outgoing notifications - #23
  * Allow encoding while constructing HTTP request for sending notification - [PR #35](https://github.com/opendistro-for-elasticsearch/alerting/pull/35)
  * Add build for Debian - #36

### Bug fixes
  * Fix update LastFullSweepTime if the index doesn't exist - #17
  * Adds more alert properties to templateArgs for context variable - #26

## 2019-04-02, Version 0.8.0

### New Features
  * Adds support for Elasticsearch 6.6.2 - [PR #8](https://github.com/opendistro-for-elasticsearch/alerting/pull/8)
  * Upgrade to latest Kotlin version - [PR #7](https://github.com/opendistro-for-elasticsearch/alerting/pull/7)

### Bug fixes
  * Fixed task name in build instructions - [PR #12](https://github.com/opendistro-for-elasticsearch/alerting/pull/12)

## 2019-01-31, Version 0.7.0

### New Features

This is the first release of the OpenDistro Elasticsearch Alerting plugin.

Allows users to create and schedule **monitors** to run periodic queries of data in Elasticsearch.
Results of periodic queries are evaluated against the monitor's **triggers** to see if they meet certain criteria.
If criteria is met, **alerts** are generated and saved in an Elasticsearch index and the user is notified by the trigger's **actions**.
Actions are messages using mustache templating created by the user that are sent to **destinations**.
Destinations are locations where action messages are sent, such as email server, slack, chime, or custom webhooks.
Alerts can be acknowledged to mute notifications.

Adds backend REST API used for basic CRUD, search operations, and testing on monitors as well as acknowledging alerts.

Adds configuration API to enable/disable monitoring.

Adds stats API to check the status of plugin and ensure everything is working as expected.

Adds API support for create, update, and deleting destinations.

### Commits

* [[`4771e6c`](https://github.com/mauve-hedgehog/opendistro-elasticsearch-alerting/commit/4771e6c5ce6f541fc84f1290ac2fd43f64f3dcb2)] Initial release for OpenDistro Elasticsearch Alerting
