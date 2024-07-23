package com.morpheus

import groovy.json.*
import groovy.sql.Sql
import com.morpheus.MorpheusUtils
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions

String.metaClass.toMap = { -> return new JsonSlurper().parseText(delegate) }

Map getDatasource(String fromSource) {
    def datasource = ["url": "", "username": "morpheus", "password": "", "driverClassName": "com.mysql.cj.jdbc.Driver"]
    switch(fromSource) {
        case "shell":
            datasource = results['getDatasourceDetails'].dataSource ?: results.getDatasourceDetails.dataSource
            break
        case "cypher":
            def CYPHER_PATH = 'secret/morpheus-mysql-datasource'
            def CYPHER_RESULT = cypher.read(CYPHER_PATH)
            if (CYPHER_RESULT != null) {
                try {
                    def CYPHER_MAP = MorpheusUtils.getJson(CYPHER_RESULT)
                    datasource = CYPHER_MAP.dataSource
                } catch (Exception e) {
                    println "\u001B[31mError parsing dataSource object from Cypher:\n${e.getMessage()}\nStacktrace:\n${e.printStackTrace()}\u001B[0m"
                }
            }
            break
        default:
            println "\u001B[33mUsing default datasource config: ${datasource}\u001B[0m"
            break
    }
    return datasource
}

static Sql getSqlClient(Map datasource) {
    def sql
    try {
        sql = Sql.newInstance(datasource.url, datasource.username, datasource.password, datasource.driverClassName)
        def testQuery = sql.rows("SELECT id FROM compute_server LIMIT 3;")
        if (testQuery.size() >= 1) {
            println """
                    \u001B[32m
          Datasource connection initialised: ${sql.getProperties()}
                    \u001B[0m
                    """
        }
    } catch (Exception e) {
        println "\u001B[31mError building sql client...\n\u001B[0m"
        println "\u001B[31mDEBUG\n--------\nerror: ${e.getMessage()}\nexception: ${e.printStackTrace()}\u001B[0m"
    }
    return sql
}

def logDebug(message) {
    println "\u001B[34m[DEBUG] -\u001B[33m ${new Date()} \u001B[34m - ${message} \u001B[0m"
}

def formatValue(value) {
    if (value instanceof Map) {
        return formatMap(value)
    } else if (value instanceof List) {
        return formatList(value)
    } else {
        return "\u001B[31m${value}\u001B[0m"
    }
}

def formatMap(map) {
    StringBuilder formattedMap = new StringBuilder()
    formattedMap.append("\u001B[31m[\u001B[0m")
    map.each { key, value ->
        formattedMap.append("\u001B[32m${key}\u001B[0m: \u001B[31m${value}\u001B[0m, ")
    }
    if (map.size() > 0) {
        formattedMap.setLength(formattedMap.length() - 2) // Remove trailing comma and space
    }
    formattedMap.append("\u001B[31m]\u001B[0m")
    return formattedMap.toString()
}

def formatList(list) {
    StringBuilder formattedList = new StringBuilder()
    formattedList.append("\u001B[31m[\u001B[0m")
    list.each { item ->
        formattedList.append("${formatValue(item)}, ")
    }
    if (list.size() > 0) {
        formattedList.setLength(formattedList.length() - 2) // Remove trailing comma and space
    }
    formattedList.append("\u001B[31m]\u001B[0m")
    return formattedList.toString()
}

def logResult(results) {
    println "\u001B[32m┌──────────────────────────────────────────────────────────────────────────────────┐\u001B[0m"
    println "\u001B[32m│                                    RESULT                                       │\u001B[0m"
    println "\u001B[32m├──────────────────────────────────────────────────────────────────────────────────┤\u001B[0m"
    results.each { row ->
        row.each { key, value ->
            println "\u001B[32m│ \u001B[0m ${key.padRight(20)} : ${formatValue(value)} \u001B[32m│\u001B[0m"
        }
        println "\u001B[32m├──────────────────────────────────────────────────────────────────────────────────┤\u001B[0m"
    }
    println "\u001B[32m└──────────────────────────────────────────────────────────────────────────────────┘\u001B[0m"
}

def main() {
    def datasource = getDatasource("cypher") ?: getDatasource("shell")
    def sql = null
    try {
        sql = getSqlClient(datasource)
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
            WHERE cscsi.compute_server_interfaces_id = ${instance.container?.serverId}"""
            
            def currentNetworkInterfaces = sql.rows(query)

            logDebug("currentNetworkInterfaces: ${currentNetworkInterfaces}")
            logDebug("EXECUTING QUERY:\n${query}")
            logResult(currentNetworkInterfaces)

            if (currentNetworkInterfaces) {
                def primary = currentNetworkInterfaces.find { it.primary_interface == true }
                logDebug("primary: ${primary}")
                def secondary = currentNetworkInterfaces.find { it.primary_interface == false }
                logDebug("secondary: ${secondary}")

                logDebug("BEFORE UPDATE - Primary: ${primary}, Secondary: ${secondary}")

                def updatePrimaryQuery = "UPDATE compute_server_interface SET primary_interface = 0 WHERE id = ${primary.interface_id} AND uuid = '${primary.uuid}'"
                def updateSecondaryQuery = "UPDATE compute_server_interface SET primary_interface = 1 WHERE id = ${secondary.interface_id} AND uuid = '${secondary.uuid}'"
                
                def updatePrimary = sql.executeUpdate(updatePrimaryQuery)
                def updateSecondary = sql.executeUpdate(updateSecondaryQuery)
                
                // Re-fetching the updated interfaces to log the changes
                def updatedPrimary = sql.firstRow("SELECT * FROM compute_server_interface WHERE id = ${primary.interface_id} AND uuid = '${primary.uuid}'")
                def updatedSecondary = sql.firstRow("SELECT * FROM compute_server_interface WHERE id = ${secondary.interface_id} AND uuid = '${secondary.uuid}'")
                
                logDebug("AFTER UPDATE - Primary: ${updatedPrimary}, Secondary: ${updatedSecondary}")

                def interfaceUpdateResults = [
                    "currentNetworkInterfaces": currentNetworkInterfaces,
                    "primary": primary,
                    "queryUpdatePrimary": updatePrimaryQuery,
                    "updatePrimaryResult": updatePrimary,
                    "secondary": secondary,
                    "queryUpdateSecondary": updateSecondaryQuery,
                    "updateSecondaryResult": updateSecondary,
                    "updatedPrimary": updatedPrimary,
                    "updatedSecondary": updatedSecondary
                ]
                
                logResult([interfaceUpdateResults])
                return interfaceUpdateResults
            }
        }
    } catch(Exception ex) {
        println "\u001B[31mERROR IN SCRIPT:\r\n${ex}\r\nStacktrace:\r\n${ex.printStackTrace()}\u001B[0m"
    } finally {
        if (sql) {
            sql.close()
        }
    }
}

main()
