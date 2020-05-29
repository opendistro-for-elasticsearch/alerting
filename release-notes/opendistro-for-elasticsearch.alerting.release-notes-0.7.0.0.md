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
