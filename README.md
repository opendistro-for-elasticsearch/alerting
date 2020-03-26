# Open Distro for Elasticsearch Alerting

The Open Distro for Elasticsearch Alerting enables you to monitor your data and send alert notifications automatically to your stakeholders. With an intuitive Kibana interface and a powerful API, it is easy to set up, manage, and monitor your alerts. Craft highly specific alert conditions using Elasticsearch's full query language and scripting capabilities.


## Highlights

Scheduled searches use [cron expressions](https://en.wikipedia.org/wiki/Cron) or intervals (e.g. every five minutes) and the Elasticsearch query DSL.

To define trigger conditions, use the Painless scripting language or simple thresholds (e.g. count > 100).

When trigger conditions are met, you can publish messages to the following destinations:

* [Slack](https://slack.com/)
* Custom webhook
* [Amazon Chime](https://aws.amazon.com/chime/)

Messages can be static strings, or you can use the [Mustache](https://mustache.github.io/mustache.5.html) templates to include contextual information.


## Documentation

Please see our [documentation](https://opendistro.github.io/for-elasticsearch-docs/).

## Setup

1. Check out this package from version control.
1. Launch Intellij IDEA, choose **Import Project**, and select the `settings.gradle` file in the root of this package. 
1. To build from the command line, set `JAVA_HOME` to point to a JDK >= 12 before running `./gradlew`.


## Build

This package is organized into subprojects, most of which contribute JARs to the top-level plugin in the `alerting` subproject.

All subprojects in this package use the [Gradle](https://docs.gradle.org/current/userguide/userguide.html) build system. Gradle comes with excellent documentation that should be your first stop when trying to figure out how to operate or modify the build.

However, to build the `alerting` plugin subproject, we also use the Elastic build tools for Gradle.  These tools are idiosyncratic and don't always follow the conventions and instructions for building regular Java code using Gradle. Not everything in `alerting` will work the way it's described in the Gradle documentation. If you encounter such a situation, the Elastic build tools [source code](https://github.com/elastic/elasticsearch/tree/master/buildSrc/src/main/groovy/org/elasticsearch/gradle) is your best bet for figuring out what's going on.


### Building from the command line

1. `./gradlew build` builds and tests all subprojects.
2. `./gradlew :alerting:run` launches a single node cluster with the alerting plugin installed.
3. `./gradlew :alerting:run -PnumNodes=3` launches a multi-node cluster with the alerting plugin installed.
4. `./gradlew :alerting:integTest` launches a single node cluster with the alerting plugin installed and runs all integ tests.
5. `./gradlew :alerting:integTest -PnumNodes=3` launches a multi-node cluster with the alerting plugin installed and runs all integ tests.
6. `./gradlew :alerting:integTest -Dtests.class="*MonitorRunnerIT"` runs a single integ test class
7. ` ./gradlew :alerting:integTest -Dtests.method="test execute monitor with dryrun"` runs a single integ test method
 (remember to quote the test method name if it contains spaces).

When launching a cluster using one of the above commands, logs are placed in `alerting/build/testclusters/integTest-0/logs/`. Though the logs are teed to the console, in practices it's best to check the actual log file.


### Debugging

Sometimes it's useful to attach a debugger to either the Elasticsearch cluster or the integ tests to see what's going on. When running unit tests, hit **Debug** from the IDE's gutter to debug the tests.
You must start your debugger to listen for remote JVM before running the below commands.

To debug code running in an actual server, run:

```
./gradlew :alerting:integTest -Des.debug # to start a cluster and run integ tests
```

OR

```
./gradlew :alerting:run --debug-jvm # to just start a cluster that can be debugged
```

The Elasticsearch server JVM will launch suspended and wait for a debugger to attach to `localhost:5005` before starting the Elasticsearch server.
The IDE needs to listen for the remote JVM. If using Intellij you must set your debug configuration to "Listen to remote JVM" and make sure "Auto Restart" is checked.
You must start your debugger to listen for remote JVM before running the commands.

To debug code running in an integ test (which exercises the server from a separate JVM), run:

```
./gradlew -Dtest.debug :alerting:integTest 
```

The test runner JVM will start suspended and wait for a debugger to attach to `localhost:5005` before running the tests.


### Advanced: Launching multi-node clusters locally

Sometimes you need to launch a cluster with more than one Elasticsearch server process.

You can do this by running `./gradlew :alerting:run -PnumNodes=<numberOfNodesYouWant>`

You can also run the integration tests against a multi-node cluster by running `./gradlew :alerting:integTest -PnumNodes=<numberOfNodesYouWant>`

You can also debug a multi-node cluster, by using a combination of above multi-node and debug steps.
But, you must set up debugger configurations to listen on each port starting from `5005` and increasing by 1 for each node.  


## Code of Conduct

This project has adopted an [Open Source Code of Conduct](https://opendistro.github.io/for-elasticsearch/codeofconduct.html).


## Security issue notifications

If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our [vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). Please do **not** create a public GitHub issue.


## Licensing

See the [LICENSE](./LICENSE.txt) file for our project's licensing. We will ask you to confirm the licensing of your contribution.


## Copyright

Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
