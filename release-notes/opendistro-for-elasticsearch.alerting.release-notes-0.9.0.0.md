## 2019-04-24, Version 0.9.0.0

### New Features
  * Adds support for Elasticsearch 6.7.1 - #19
  * Add http proxy support to outgoing notifications - #23
  * Allow encoding while constructing HTTP request for sending notification - [PR #35](https://github.com/opendistro-for-elasticsearch/alerting/pull/35)
  * Add build for Debian - #36

### Bug Fixes
  * Fix update LastFullSweepTime if the index doesn't exist - #17
  * Adds more alert properties to templateArgs for context variable - #26
