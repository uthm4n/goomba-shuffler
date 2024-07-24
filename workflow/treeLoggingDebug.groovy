package com.morpheus

import groovy.json.*
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.control.CompilePhase
import groovy.sql.Sql
import com.morpheus.MorpheusUtils
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions

class TreeLogger {
    private static int level = 0
    private static String prefix = "│   "
    private static String branch = "├── "
    private static String lastBranch = "└── "

    static void log(String message) {
        def indent = prefix * level
        println("${indent}${branch}${message}")
    }

    static void logLast(String message) {
        def indent = prefix * level
        println("${indent}${lastBranch}${message}")
    }

    static void increaseLevel() {
        level++
    }

    static void decreaseLevel() {
        level = Math.max(0, level - 1)
    }
}

def getDataSource(String fromSource) {
    def dataSource = ["url": "", "username": "morpheus", "password": "", "driverClassName": "com.mysql.cj.jdbc.Driver"]
    switch (fromSource) {
        case "shell":
            dataSource = results['getDataSourceDetails'].dataSource ?: results.getDataSourceDetails.dataSource
            break
        case "cypher":
            def CYPHER_PATH = 'secret/morpheus-mysql-datasource'
            def CYPHER_RESULT = cypher.read(CYPHER_PATH)
            if (CYPHER_RESULT != null) {
                try {
                    def CYPHER_MAP = MorpheusUtils.getJson(CYPHER_RESULT)
                    dataSource = CYPHER_MAP.dataSource
                } catch (Exception e) {
                    TreeLogger.log("Error parsing dataSource object from Cypher: ${e.getMessage()}")
                }
            }
            break
        default:
            TreeLogger.log("Using default datasource config: ${dataSource}")
            break
    }
    return dataSource
}

static Sql getSqlClient(Map dataSource) {
    def sql
    try {
        sql = Sql.newInstance(dataSource.url, dataSource.username, dataSource.password, dataSource.driverClassName)
        def testQuery = sql.rows("SELECT id FROM compute_server LIMIT 3;")
        if (testQuery.size() >= 1) {
            TreeLogger.log("DataSource connection initialized: ${sql.getProperties()}")
        }
    } catch (Exception e) {
        TreeLogger.log("Error building sql client...\n${e.getMessage()}\n${e.printStackTrace()}")
    }
    return sql
}

def logDebug(message) {
    TreeLogger.log(message)
}

def main() {
    TreeLogger.log("Starting main execution")
    def dataSource = getDataSource("cypher") ?: getDataSource("shell")
    def sql = null
    try {
        sql = getSqlClient(dataSource)
        if (sql) {
            def query = """
            SELECT csi.id AS interface_id,
                   cscsi.compute_server_interfaces_id AS server_id,
                   csi.uuid,
                   csi.primary_interface,
                   csi.display_order
            FROM compute_server_interface csi 
            INNER JOIN compute_server_compute_server_interface cscsi 
            ON cscsi.compute_server_interface_id = csi.id 
            WHERE cscsi.compute_server_interfaces_id = ${instance.container?.serverId}
            """
            TreeLogger.log("Executing QUERY:\n${query}")
            def currentNetworkInterfaces = sql.rows(query)
            logDebug("currentNetworkInterfaces: ${currentNetworkInterfaces}")
            if (currentNetworkInterfaces) {
                def primary = currentNetworkInterfaces.find { it.primary_interface == true }
                TreeLogger.increaseLevel()
                logDebug("primary: ${primary}")
                def secondary = currentNetworkInterfaces.find { it.primary_interface == false }
                logDebug("secondary: ${secondary}")
                TreeLogger.decreaseLevel()
                logDebug("BEFORE UPDATE - Primary: ${primary}, Secondary: ${secondary}")
                def updatePrimaryQuery = "UPDATE compute_server_interface SET primary_interface = 0 WHERE id = ${primary.interface_id}"
                def updateSecondaryQuery = "UPDATE compute_server_interface SET primary_interface = 1 WHERE id = ${secondary.interface_id}"
                TreeLogger.increaseLevel()
                logDebug("updatePrimaryQuery: ${updatePrimaryQuery}")
                logDebug("updateSecondaryQuery: ${updateSecondaryQuery}")
                TreeLogger.decreaseLevel()
                sql.executeUpdate(updatePrimaryQuery)
                sql.executeUpdate(updateSecondaryQuery)
                logDebug("AFTER UPDATE:\r\n${currentNetworkInterfaces}")
            }
        }
    } catch (Exception e) {
        TreeLogger.log("Error executing SQL queries: ${e.getMessage()}")
    } finally {
        if (sql) {
            sql.close()
            TreeLogger.log("SQL connection closed.")
        }
    }
    TreeLogger.log("End of main execution")
}

main()
