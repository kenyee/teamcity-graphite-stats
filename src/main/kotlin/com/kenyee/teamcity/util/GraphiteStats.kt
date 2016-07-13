package com.kenyee.teamcity.util

import org.jetbrains.teamcity.rest.BuildLocator
import org.jetbrains.teamcity.rest.BuildStatus
import org.jetbrains.teamcity.rest.TeamCityInstance
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.*

/*
 * Main graphite polling loop
 *
 * Data sent to graphite:
 * <prefix>.queuesize
 * <prefix>.build.<config>.queuetime
 * <prefix>.build.<config>.buildtime
 */
class GraphiteStats {

    fun someLibraryMethod(): Boolean {
        return true
    }

    companion object {
        private val LOG = LoggerFactory.getLogger("GraphiteStats")

        @JvmStatic fun main(args: Array<String>) {
            val configPath = if (args.size > 0) args[0] else "config.yml"
            val configInfo = loadConfig(configPath)
            if (configInfo == null) {
                println("ERROR - Configuration error!")
                System.exit(1);
            }

            runLoop(configInfo!!)
        }

        fun runLoop(configInfo: ConfigInfo) {
            val tcServer = TeamCityInstance.httpAuth(configInfo.teamcityServer,
                    configInfo.username.toString(), configInfo.password.toString())

            var lastPollTime = Date()
            while (true) {
                try {
                    val queuedBuilds = tcServer.queuedBuilds().list()
                    LOG.debug("Queue length: ${queuedBuilds.size}")
                    sendGraphiteStat(configInfo, configInfo.getQueueLengthMetricName(), queuedBuilds.size.toString())
                    if (LOG.isDebugEnabled()) {
                        queuedBuilds.map({ it -> LOG.debug(it.toString()) })
                    }

                    val successfulBuildsLocator = (tcServer.builds()
                            .withStatus(BuildStatus.SUCCESS)
                            .sinceDate(lastPollTime)
                            .limitResults(configInfo.maxBuilds))
                    if (successfulBuildsLocator is BuildLocator) {
                        val successfulBuilds = successfulBuildsLocator.list();
                        LOG.debug("Successful Build count: ${successfulBuilds.size}")

                        successfulBuilds
                                .map({
                                    if (it.fetchFinishDate() > lastPollTime) {
                                        lastPollTime = it.fetchFinishDate()
                                    }
                                })
                        successfulBuilds
                                .filter({ it ->
                                    var includeBuild = true
                                    for (exclusion in configInfo.excludeProjects) {
                                        if (exclusion.toRegex().matches(it.buildConfigurationId)) {
                                            includeBuild = false
                                            break
                                        }
                                    }
                                    includeBuild
                                })
                                .map({
                                    val buildTime = (it.fetchFinishDate().time - it.fetchStartDate().time) / 1000
                                    val queueTime = (it.fetchStartDate().time - it.fetchQueuedDate().time) / 1000

                                    LOG.debug("$it $buildTime $queueTime")
                                    sendGraphiteStat(configInfo, configInfo.getBuildRunTimeMetricName(it.buildConfigurationId), buildTime.toString())
                                    sendGraphiteStat(configInfo, configInfo.getBuildWaitTimeMetricName(it.buildConfigurationId), queueTime.toString())
                                });
                    }
                } catch (e: Exception) {
                    LOG.error("Error reading from Teamcity: ", e)
                } catch (e: java.lang.Error) {
                    LOG.error("Error connecting to Teamcity: ", e)
                }

                Thread.sleep(configInfo.pollPeriodSecs.times(1000).toLong());
            }
        }

        fun loadConfig(configPath: String): ConfigInfo? {
            val configFile = File(configPath);
            println("Loading configuration from: " + configFile.absolutePath)
            if (!configFile.exists()) {
                println("ERROR - Configuration file not found!")
                return null
            }
            val yaml = Yaml(SafeConstructor())
            val configData = yaml.load(FileInputStream(configFile))
            if (configData is Map<*, *>) {
                val graphiteServer = configData.get("graphite").toString()
                if (graphiteServer.isNullOrEmpty()) {
                    println("ERROR - Graphite server must be specified")
                }

                val prefix = configData.get("prefix").toString()
                val teamcityServer = configData.get("teamcity").toString()
                if (teamcityServer.isNullOrEmpty()) {
                    println("ERROR - Teamcity server URL must be specified")
                }

                val username = configData.get("username").toString()
                val password = configData.get("password").toString()

                var pollPeriodSecs = configData.get("pollsecs") as Int?
                if (pollPeriodSecs == null) {
                    println("Poll period not specified...defaulting to 10sec polling")
                    pollPeriodSecs = 10
                }

                var maxBuilds = configData.get("maxbuilds") as Int?
                if (maxBuilds == null) {
                    println("Max build limit for period not specified...defaulting to 100")
                    maxBuilds = 10
                }

                @Suppress("UNCHECKED_CAST")
                var exclusions = configData.get("exclude") as List<String>?
                if (exclusions == null) {
                    exclusions = ArrayList<String>()
                }

                val configInfo = ConfigInfo(graphiteServer, prefix, teamcityServer, username, password,
                        pollPeriodSecs, maxBuilds, exclusions)

                return configInfo
            }

            return null
        }

        fun sendGraphiteStat(configInfo: ConfigInfo, metricName: String, metricValue: String) {
            val message = metricName.formatMetric(metricValue)
            val socket = Socket(configInfo.graphiteServer, 2003)
            val writer = OutputStreamWriter(socket.getOutputStream())
            try {
                writer.write(message);
                writer.flush();

            } catch (e: IOException) {
                LOG.error("Error writing to graphite: ", e)
            }
        }

        fun String.formatMetric(metricValue: String): String {
            return this + " " + metricValue + " " + Math.round(System.currentTimeMillis() / 1000.0) + "\n";
        }
    }

    data class ConfigInfo(
            val graphiteServer: String,
            val prefix: String?,
            val teamcityServer : String,
            val username : String?,
            val password : String?,
            val pollPeriodSecs : Int,
            val maxBuilds : Int,
            val excludeProjects : List<String>) {

        fun getQueueLengthMetricName(): String {
            return prefix + ".queuesize"
        }

        fun getBuildWaitTimeMetricName(buildConfigurationId: String): String {
            return prefix + ".build." + buildConfigurationId + ".queuetime"
        }

        fun getBuildRunTimeMetricName(buildConfigurationId: String): String {
            return prefix + ".build." + buildConfigurationId + ".buildtime"
        }
    }
}
