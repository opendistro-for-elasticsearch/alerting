#What?

OpenDistro Plugins for Elasticsearch. Currently includes alerting plugin.

#Developing

## Setup

1. Checkout this package from version control. 
1. Launch Intellij IDEA, Choose Import Project and select the `settings.gradle` file in the root of this package. 
1. To build from command line set `JAVA_HOME` to point to a JDK >=11 before running `./gradlew`

## Build

This package is organised into subprojects most of which contribute JARs to the top-level ES plugin in the `alerting` subproject. 

All subprojects in this package use the [Gradle](https://docs.gradle.org/4.10.2/userguide/userguide.html) build system. Gradle comes with excellent documentation which should be your first stop when trying to figure out how to operate or modify the build. 

However to build the `alerting` plugin subproject we also use the Elastic build tools for Gradle.  These tools are idiosyncratic and don't always follow the conventions and instructions for building regular java code using Gradle. Not everything in `alerting` will work the way it's described in the Gradle documentation. If you encounter such a situation the Elastic build tools [source code](https://github.com/mauve-hedgehog/opendistro-elasticsearch/tree/opendistroforelasticsearch-0.7/buildSrc/src/main/groovy/org/elasticsearch/gradle) is your best bet for figuring out what's going on. 

### Building from command line

1. `./gradlew release` builds and tests all subprojects
1. `./gradlew :alerting:run` launches a single node cluster with the alerting plugin installed
1. `./gradlew :alerting:integTest` launches a single node cluster with the alerting plugin installed and runs all integ tests
1. ` ./gradlew :alerting:integTest --tests="**.test execute foo"` runs a single integ test class or method
 (remember to quote the test method name if it contains spaces).

When launching a cluster using one of the above commands logs are placed in `alerting/build/cluster/run node0/elasticsearch-<version>/logs`. Though the logs are teed to the console, in practices it's best to check the actual log file.
 
### Building from the IDE
The only IDE we support is IntelliJ IDEA.  It's free, it's open source, it works. The gradle tasks above can also be launched from IntelliJ's Gradle toolbar and the extra parameters can be passed in via the Launch Configurations VM arguments. 

### Debugging

Sometimes it's useful to attach a debugger to either the ES cluster or the integ tests to see what's going on. When running unit tests you can just hit 'Debug' from the IDE's gutter to debug the tests.  To debug code running in an actual server run:

```
./gradlew :alerting:integTest --debug-jvm # to start a cluster and run integ tests
OR
./gradlew :alerting:run --debug-jvm # to just start a cluster that can be debugged
```

The ES server JVM will launch suspended and wait for a debugger to attach to `localhost:8000` before starting the ES server.

To debug code running in an integ test (which exercises the server from a separate JVM) run:

```
./gradlew -Dtest.debug :alerting:integTest 
```

The **test runner JVM** will start suspended and wait for a debugger to attach to `localhost:5005` before running the tests.

### Advanced: Launching multi node clusters locally

Sometimes you need to launch a cluster with more than one ES server process. The `startMultiNodeXX` tasks help with this. There are two ways to use these:

#### All nodes are started and stopped together

If you need a multi node cluster where all nodes are started together use: 

```
./gradlew -PnumNodes=2 startMultiNode ... # to launch 2 nodes

```

***Remember to manually kill the nodes when you're done!!***

#### Nodes join and leave the cluster independently

If you need a multi node cluster where you'd like to be able to add and kill each node independently use:

```
./gradlew startMultiNode1 
./gradlew startMultiNode2
... AND SO ON
```
***Remember to manually kill the nodes when you're done!!***
