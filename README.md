# teamcity-graphite-stats
Kotlin project for polling stats from Teamcity.  Statistics include the build queue size which is the most useful statistic for whether your build server needs attention.

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

You can run the .jar file with the config.yml as a parameter.

Credits
-------

The original teamcity REST API code is from [Teamcity's REST API Kotlin Example](https://github.com/JetBrains/teamcity-rest-client).  I had to modify it to hit extra endpoints it didn't support and contributed code back.

