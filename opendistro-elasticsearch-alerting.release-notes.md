## Version 1.6.0.0 (Current)

### New Features
  * Adds support for Elasticsearch 7.6.1 - [PR #186](https://github.com/opendistro-for-elasticsearch/alerting/pull/186)

## 2020-02-07, Version 1.4.0.0

### New Features
  * Adds settings to disable alert history and delete old history indices - [PR #143](https://github.com/opendistro-for-elasticsearch/alerting/pull/143)
  * Adds support for Elasticsearch 7.4.2 - [PR #141](https://github.com/opendistro-for-elasticsearch/alerting/pull/141)
  * Adds maven publish task for notification subproject for other plugins to utilize - [PR #97](https://github.com/opendistro-for-elasticsearch/alerting/pull/97)

### Bug Fixes
  * Removes unsupported HttpInput types which was breaking Alerting Kibana - [PR #162](https://github.com/opendistro-for-elasticsearch/alerting/pull/162)
  * Fixes missing constructors for Stats when updating to 7.4.2 - [PR #142](https://github.com/opendistro-for-elasticsearch/alerting/pull/142)
  * Fixes multi node alerting stats API - [PR #128](https://github.com/opendistro-for-elasticsearch/alerting/pull/128)

## 2019-11-14, Version 1.3.0.0

### Bug Fixes
  * Fixes allowing interval to be set with 0 or negative values - [PR #92](https://github.com/opendistro-for-elasticsearch/alerting/pull/92)
  * Cleanup ElasticThreadContextElement - [PR #95](https://github.com/opendistro-for-elasticsearch/alerting/pull/95)

## 2019-09-24, Version 1.2.0.1

### Bug Fixes
  * Updated execute API to keep thread context - [PR #90](https://github.com/opendistro-for-elasticsearch/alerting/pull/90)

## 2019-09-20, Version 1.2.0.0

### New Features
  * Adds support for Elasticsearch 7.2.0 - [PR #89](https://github.com/opendistro-for-elasticsearch/alerting/pull/89)

### Bug Fixes
  * Fixes integTestRunner build task - [PR #83](https://github.com/opendistro-for-elasticsearch/alerting/pull/83)

## 2019-07-25, Version 1.1.0.0

### New Features
  * Adds support for Elasticsearch 7.1.1 - [PR #74](https://github.com/opendistro-for-elasticsearch/alerting/pull/74)
  * Return stack trace for errors in Groovy/Painless script in UI when creating a trigger - [PR #72](https://github.com/opendistro-for-elasticsearch/alerting/pull/72)

## 2019-06-25, Version 1.0.0.0

### New Features
  * Adds support for Elasticsearch 7.0.1 - [PR #41](https://github.com/opendistro-for-elasticsearch/alerting/pull/41)
  * Adds support for throttling on actions - [PR #48](https://github.com/opendistro-for-elasticsearch/alerting/pull/48)

### Bug Fixes
  * Validate all 2XX status code in Webhook response  - [PR #50](https://github.com/opendistro-for-elasticsearch/alerting/pull/50)
  * Allows encoding while constructing HTTP request for sending notification - [PR #35](https://github.com/opendistro-for-elasticsearch/alerting/pull/35)
  * Convert AlertMover/MonitorRunner to coroutines - [PR #11](https://github.com/opendistro-for-elasticsearch/alerting/pull/11)

## 2019-04-24, Version 0.9.0.0

### New Features
  * Adds support for Elasticsearch 6.7.1 - #19
  * Add http proxy support to outgoing notifications - #23
  * Allow encoding while constructing HTTP request for sending notification - [PR #35](https://github.com/opendistro-for-elasticsearch/alerting/pull/35)
  * Add build for Debian - #36

### Bug Fixes
  * Fix update LastFullSweepTime if the index doesn't exist - #17
  * Adds more alert properties to templateArgs for context variable - #26

## 2019-04-02, Version 0.8.0.0

### New Features
  * Adds support for Elasticsearch 6.6.2 - [PR #8](https://github.com/opendistro-for-elasticsearch/alerting/pull/8)
  * Upgrade to latest Kotlin version - [PR #7](https://github.com/opendistro-for-elasticsearch/alerting/pull/7)

### Bug Fixes
  * Fixed task name in build instructions - [PR #12](https://github.com/opendistro-for-elasticsearch/alerting/pull/12)

## 2019-01-31, Version 0.7.0.0

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
