package com.morpheus

import groovy.json.*
import groovy.sql.Sql

def datasource = results['getDatasourceDetails'].dataSource ?: results.getDatasourceDetails.dataSource
println """
DEBUG
-----
datasource:
${datasource}

"""

def servers = [id: "${instance.containers?.serverId}"]
servers.each { server -> 
	server.value = server.value.replaceAll("\\[|\\]", "")
    server.value = Integer.parseInt(server.value)
}
println "serverInfo: ${servers}"

def queries = [
    "networkInterfaceSet": "",
    "interfacesSelect": "",
    "interfaceInfo": """
        SELECT compute_server_interfaces_id AS server_id, 
            csi.id AS interface_id, 
            csi.name, 
            csi.external_id, 
            csi.mac_address, 
            csi.active, 
            csi.primary_interface 
        FROM compute_server_compute_server_interface cscsi 
        INNER JOIN compute_server_interface csi 
        ON csi.id=cscsi.compute_server_interface_id 
        WHERE compute_server_interfaces_id = ${servers.id}
    """
]

try {
    def sql = Sql.newInstance(datasource.url, datasource.username, datasource.password, datasource.driverClassName)
    println "Executing SQL query: ${queries.interfaceInfo}\r\n"
    def interfaceResultSet = sql.rows(queries.interfaceInfo)
    println "Interfaces before update:\r\n${JsonOutput.prettyPrint(JsonOutput.toJson(interfaceResultSet))}"

    def primaryInterface = interfaceResultSet.find { it.primary_interface }
    def secondaryInterface = interfaceResultSet.find { !it.primary_interface }

    def updatePrimaryToSecondary = """
        UPDATE compute_server_interface
        SET primary_interface = false
        WHERE id = ${primaryInterface.interface_id}
    """
    sql.execute(updatePrimaryToSecondary)

    def updateSecondaryToPrimary = """
        UPDATE compute_server_interface
        SET primary_interface = true
        WHERE id = ${secondaryInterface.interface_id}
    """
    sql.execute(updateSecondaryToPrimary)

    interfaceResultSet = sql.rows(queries.interfaceInfo)
    println "Interfaces after update:\r\n${JsonOutput.prettyPrint(JsonOutput.toJson(interfaceResultSet))}"

    println "Interfaces updated successfully."
} catch (Exception e) {
    println "\u001B[31mError executing SQL query: ${e.message}\u001B[0m"
    e.printStackTrace()
    throw e
}
