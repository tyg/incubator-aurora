# Aurora

This is a snapshot of the source code for the scheduler component of
Aurora, a mesos framework.  This code is not meant for redistribution,
but is made available while the code is in consideration for Apache
incubation.

**There will be no useful commits here, this is a staging area only.**

## Building
These instructions have been tested on Mac OS X 10.8 and Ubuntu 13.04. You'll need build tools,
OpenJDK 1.6+, and Gradle.

### Compiling and running tests

The default gradle task will compile aurora and run all tests.

    $ gradle build

### Starting an Isolated Scheduler ("Demo Mode")
To spin up a local scheduler in "demo mode". The web UI will appear on http://localhost:8081.
http://localhost:8081/scheduler shows the state of all jobs under Aurora's control.

    $ gradle distZip && ./demo.sh
