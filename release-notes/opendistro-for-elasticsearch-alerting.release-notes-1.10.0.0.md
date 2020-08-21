## 2020-08-18, Version 1.10.0.0

Compatible with Elasticsearch 7.9.0

### Enhancements
  * Add action to 'DELETE /_alerting/destinations/{id}' ([#233](https://github.com/opendistro-for-elasticsearch/alerting/pull/233))
  * Add action to '/_alerting/monitor/{id}', '/_alerting/monitor/_search' ([#234](https://github.com/opendistro-for-elasticsearch/alerting/pull/234))
  * Add action to 'CREATE /_alerting/destinations/' ([#235](https://github.com/opendistro-for-elasticsearch/alerting/pull/235))

### Infrastructure
  * Support integration testing against remote security enabled clustering ([#213](https://github.com/opendistro-for-elasticsearch/alerting/pull/213))
  * Add coverage upload in build workflow and add badges in README ([#223](https://github.com/opendistro-for-elasticsearch/alerting/pull/223))
  * Add Codecov configuration to set a coverage threshold to pass the check on a commit ([#231](https://github.com/opendistro-for-elasticsearch/alerting/pull/231))

### Maintenance
  * Adds support for Elasticsearch 7.9.0 ([#238](https://github.com/opendistro-for-elasticsearch/alerting/pull/238))
  * Upgrade the vulnerable dependencies versions of Kotlin and 'commons-codec' ([#230](https://github.com/opendistro-for-elasticsearch/alerting/pull/230))