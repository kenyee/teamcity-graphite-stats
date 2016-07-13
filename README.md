# teamcity-graphite-stats
Kotlin project for polling stats from Teamcity.  Statistics include the build queue size which is the most useful statistic for whether your build server needs attention.  It will also collect stats for the build time and queue time of each project being built.  All times are in seconds.

config.yml
----------

This is a YAML configuration file that determines what statistics are retrieved and sent to graphite.  Here's an example file:

````
graphite: graphite.local
prefix: teamcity.
teamcity: http://teamcity.local/
username: graphite
password: somepassword
pollsecs: 10
maxbuilds: 100
exclude:
    - AgentMaintenance_CleanIOSAgents
    - "[a-zA-Z0-9_]*Trigger[a-zA-Z0-9_]*" 
````

The parameters in the YAML file are as follows:

- graphite: the hostname or IP of your graphite server
- teamcity: the URL of the Teamcity server
- prefix: the graphite statistics name prefix for data collected
- username: the username of the Teamcity account used for polling from your Teamcity server...should be a separate account with read-only access to all projects
- password: password for the above Teamcity account
- pollsecs: seconds between each poll attempt
- maxbuilds: max # builds to read from Teamcity when polling (this should be a value that guarantees some overlap because we can't tell Teamcity that we only want new builds as of build #N)
- exclude: project names to exclude when generating project build/queue times

You can run the .jar file with the config.yml as the first parameter (e.g., "java -jar teamcity-graphite-stats.jar <somepath>/config.yml") or it will default to look for the config.yml file in the current directory.  This should be run as a headless service on whatever platform you're going to run this data collector on.  You can save the output into a log file to get more info on what it's doing.

Building
--------
It's a standard Gradle project.  If you don't have gradle installed, you can type in "./gradlew assemble" at the top of the project and it will build the uild/lib/teamcity-graphite-stats.jar file which you need a Java runtime or JDK to run.

Credits
-------

The original teamcity REST API code is from [Teamcity's REST API Kotlin Example](https://github.com/JetBrains/teamcity-rest-client).  I had to modify it to hit extra endpoints it didn't support and contributed code back for this.
