# Document-Level Alerting RFC

The purpose of this request for comments (RFC) is to introduce our plans to enhance the Alerting plugin with document-level alerting functionality and collect feedback and discuss our plans with the community. This RFC is meant to cover the high-level functionality and does not go into implementation details and architecture.

## Problem Statement

Currently, the Alerting plugin does not support a simple way to create Monitors that alert on each event grouped by some dimension (or document field in Elasticsearch’s case).

A common alerting use case is monitoring an index that is ingesting logs that represent the health of hosts. In this case, a Monitor would be set up to alert when any hosts exceeded a certain threshold on various metrics (CPU, JVM memory, etc.) and the expectation is that each event (host meeting conditions) is notified to the user.

This use case can somewhat be accomplished today through custom extraction queries during the Monitor definition but it is not intuitive and only a single alert is created for a group of events. Due to only having a single alert in these scenarios, individual events can be missed if an alert is already active for a Trigger.

## Proposed Solution

The proposed solution is to offer more granular alerting by allowing alerts to be created based on unique values of fields. This means that there could potentially be multiple alerts even for a single trigger condition, ensuring that every event is captured.

### Aggregation

We plan to let Monitors configure at least one field to group by. This is to support situations in which the user wants a separate alert for each unique field value where trigger conditions are being satisfied, such as when monitoring the health of a host. Alerts for new field values (ex. hosts) should be generated at least by the next execution time (which currently can be as low as 1 minute).

### De-duping

As the number of potential concurrent alerts per monitor increases, there will need to be mechanisms in place to prevent being overwhelmed. We plan to support de-duping alerts if there is already an alert for a particular field value. This will ensure important information is propagated through alerts while avoiding flooding users during high event count scenarios.

### Suppression

Similar to controlling the possible flood of alerts, the resulting actions/notifications that the user receives will need to be controlled. We plan to offer suppression options to configure how often actions/notifications are executed for generated alerts. The user should have the option to be notified on each new alert, reoccurrences of existing alerts or state changes to existing alerts. There should be enough flexibility to notify frequently or infrequently, depending on what the user considers significant enough to be notified on. 

## Providing Feedback

If you have comments or feedback on our plans for Document-Level Alerting, please comment on the [GitHub issue](https://github.com/opendistro-for-elasticsearch/alerting/issues/326) in this project to discuss.
