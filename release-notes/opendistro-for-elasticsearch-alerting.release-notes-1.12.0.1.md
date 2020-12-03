## 2020-12-03, Version 1.12.0.1

Compatible with Elasticsearch 7.10.0

### Features
  * Allow for http method selection in custom webhook ([#101](https://github.com/opendistro-for-elasticsearch/alerting/pull/101))

### Enhancements
  * Run /_execute in User context ([#312](https://github.com/opendistro-for-elasticsearch/alerting/pull/312))
  * Support filterBy in update/delete destination/monitor APIs ([#311](https://github.com/opendistro-for-elasticsearch/alerting/pull/311))

### Bug Fixes
  * get user info from threadcontext ([#289](https://github.com/opendistro-for-elasticsearch/alerting/pull/289))
  * Fix filter by user.backendroles and integ tests for it ([#290](https://github.com/opendistro-for-elasticsearch/alerting/pull/290))
  * Check empty user object for the AD monitor ([#304](https://github.com/opendistro-for-elasticsearch/alerting/pull/304))

### Infrastructure
  * Add integ security tests with a limited user ([#313](https://github.com/opendistro-for-elasticsearch/alerting/pull/313))

### Maintenance
  * Adds support for Elasticsearch 7.10.0 ([#300](https://github.com/opendistro-for-elasticsearch/alerting/pull/300))
  * Move to common-utils-1.12.0.2 version ([#314](https://github.com/opendistro-for-elasticsearch/alerting/pull/314))
