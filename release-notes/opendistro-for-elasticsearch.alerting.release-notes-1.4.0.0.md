## 2020-02-07, Version 1.4.0.0

### New Features
  * Adds settings to disable alert history and delete old history indices - [PR #143](https://github.com/opendistro-for-elasticsearch/alerting/pull/143)
  * Adds support for Elasticsearch 7.4.2 - [PR #141](https://github.com/opendistro-for-elasticsearch/alerting/pull/141)
  * Adds maven publish task for notification subproject for other plugins to utilize - [PR #97](https://github.com/opendistro-for-elasticsearch/alerting/pull/97)

### Bug Fixes
  * Removes unsupported HttpInput types which was breaking Alerting Kibana - [PR #162](https://github.com/opendistro-for-elasticsearch/alerting/pull/162)
  * Fixes missing constructors for Stats when updating to 7.4.2 - [PR #142](https://github.com/opendistro-for-elasticsearch/alerting/pull/142)
  * Fixes multi node alerting stats API - [PR #128](https://github.com/opendistro-for-elasticsearch/alerting/pull/128)
