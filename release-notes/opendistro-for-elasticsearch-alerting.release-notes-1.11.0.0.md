## 2020-10-19, Version 1.11.0.0

## Features
  * Add getDestinations and getAlerts API ([#260](https://github.com/opendistro-for-elasticsearch/alerting/pull/260))

### Enhancements
  *  Add logged-on User details to the Monitor and Destination ([#255](https://github.com/opendistro-for-elasticsearch/alerting/pull/255))
  *  Inject roles for alerting background jobs ([#259](https://github.com/opendistro-for-elasticsearch/alerting/pull/259))
  *  Terminate security for email transport handlers ([#262](https://github.com/opendistro-for-elasticsearch/alerting/pull/262))
  *  Add AllowList for Destinations ([#263](https://github.com/opendistro-for-elasticsearch/alerting/pull/263))
  *  Add User to Alerts, add filter by back end roles ([#264](https://github.com/opendistro-for-elasticsearch/alerting/pull/264))
  *  Change AlertError message and remove deny-list destinations check during monitor creation ([#270](https://github.com/opendistro-for-elasticsearch/alerting/pull/270))
  *  Use common-utils from maven, use withContext instead runBlocking ([#273](https://github.com/opendistro-for-elasticsearch/alerting/pull/273))

### Bug fixes
  * Fix socket timeout exception, moved authuser to transport handler, moved User to commons ([#266](https://github.com/opendistro-for-elasticsearch/alerting/pull/266))
  * Misc fixes for 1.11 release ([#274](https://github.com/opendistro-for-elasticsearch/alerting/pull/274))

### Infrastructure
  * Add tests related to Email Destination ([#258](https://github.com/opendistro-for-elasticsearch/alerting/pull/258))
  * Minor change to a unit test ([#261](https://github.com/opendistro-for-elasticsearch/alerting/pull/261)) 

### Documentation
  * better link for documentation ([#265](https://github.com/opendistro-for-elasticsearch/alerting/pull/265)) 

### Maintenance
  * Adds support for Elasticsearch 7.9.1 ([#251](https://github.com/opendistro-for-elasticsearch/alerting/pull/251))
  
### Refactoring
  * Rename actions ([#256](https://github.com/opendistro-for-elasticsearch/alerting/pull/256))