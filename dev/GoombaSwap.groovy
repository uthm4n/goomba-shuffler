package com.morpheus

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import com.morpheus.MorpheusUtils
import java.util.Base64

serverId = "${instance.containers[0].server.id}"
dbPassword = "${cypher.read('secret/dbPassword')}"
host = "127.0.0.1"

RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
MAGENTA = '\033[35m'
NC = '\033[0m'
DARK_YELLOW = '\033[38;5;58m'

def executeQuery(String query) {
    println "${GREEN}${BLUE}EXECUTING QUERY:${NC} \r\n${MAGENTA}$query${NC}"
    
    def command = ["/opt/morpheus/embedded/mysql/bin/mysql", "--table", "-u", "morpheus", "-p${dbPassword}", "-h", host, "morpheus", "-e", query]
    def process = command.execute()
    process.waitFor()
    def result = process.in.text
    println "Processing result..."
    Thread.sleep(7000) 
    println "${GREEN}${BLUE}RESULT:${NC} \r\n${RED}$result${NC}"
    
    return result
}

List<Map<String, String>> parseTableResult(String result) {
    def lines = result.split('\n').findAll { it.trim() }
    if (lines.size() < 3) return [] 

    def headers = lines[1].trim().split(/\s*\|\s*/).findAll { it }
    def dataLines = lines[3..-2] 

    List<Map<String, String>> parsedResult = []
    dataLines.each { line ->
        def values = line.trim().split(/\s*\|\s*/).findAll { it }
        def row = [:]
        headers.eachWithIndex { header, index ->
            row[header.trim()] = values[index].trim()
        }
        parsedResult << row
    }

    return parsedResult
}

void getServerInterfaceMappings() {
    def query = """
    SELECT * FROM compute_server_compute_server_interface 
    WHERE compute_server_interfaces_id = '$serverId';
    """
    executeQuery(query)
}

void getInterfaceData() {
    def query = """
    SELECT csi.id AS interface_id,
        cscsi.compute_server_interfaces_id AS server_id,
        csi.uuid,
        csi.primary_interface,
        csi.display_order
    FROM compute_server_interface csi 
    INNER JOIN compute_server_compute_server_interface cscsi 
    ON cscsi.compute_server_interface_id = csi.id 
    WHERE cscsi.compute_server_interfaces_id = '$serverId';
    """
    executeQuery(query)
}

def getInstanceJSON() {
    def query = """
    SELECT JSON_OBJECT(
        'instance', 
        JSON_OBJECT(
            'id', ${instance.id},
            'server', JSON_OBJECT(
                'id', $serverId,
                'interfaces', JSON_ARRAYAGG(
                    JSON_OBJECT(
                        'id', CSCSI.compute_server_interface_id,
                        'uuid', CSI.uuid,
                        'name', CSI.name,
                        'primary_interface', CSI.primary_interface,
                        'display_order', CSI.display_order,
                        'row', CSI.display_order
                    )
                )
            )
        )
    ) AS json_result
    FROM compute_server_compute_server_interface CSCSI
    INNER JOIN compute_server_interface CSI ON CSCSI.compute_server_interface_id = CSI.id
    WHERE CSCSI.compute_server_interfaces_id = '$serverId';
    """
    def result = executeQuery(query)
	
    def json_result_extracted = result.find(/(?s)\{.*\}/)
    def instanceMap = MorpheusUtils.getJson(json_result_extracted)
    println "${GREEN}${BLUE}MAP:${NC} \r\n${RED}${instanceMap}${NC}"
    println "${GREEN}${BLUE}JSON:${NC} \r\n${RED}${JsonOutput.prettyPrint(json_result_extracted)}${NC}"
	
    return result
}

def swapInterfaces(Map instanceMap) {
    println "Swapping interfaces in map..."
    def interfaces = instanceMap.instance.server.interfaces
    interfaces.each { network_interface ->
	    // Decode the base64 part
	    byte[] PRIMARY_INTERFACE_BASE64 = Base64.decoder.decode(network_interface.primary_interface.split(':')[2])
	    def IS_PRIMARY = new String(PRIMARY_INTERFACE_BASE64)
	    
	    println "ID: ${network_interface.id}"
	    println "PRIMARY: ${network_interface.primary_interface}"
	    println "Decoding primary_interface value ${network_interface.primary_interface} for ${network_interface.id}..."
	    println "DECODED: ${new String(PRIMARY_INTERFACE_BASE64)}"
	    println "BYTES: ${PRIMARY_INTERFACE_BASE64}"
	    
	    // Check if the first byte equals 0 (meaning it's not primary)
	    if (IS_PRIMARY[0] == 0) { 
	        println "network interface ${network_interface.id} IS NOT PRIMARY <${IS_PRIMARY}\r\n>" 
	    } else { 
	        println "network interface ${network_interface.id} IS PRIMARY <${primary_interface_decoded}>\r\n"
	    }
    }
    	network_interface.primary_interface = IS_PRIMARY[0] == 0
	instanceMap.instance.server.interfaces = interfaces
    	println "${GREEN}${BLUE}DECODED INTERFACE CONFIGURATIONS:${NC} \r\n${RED}${JsonOutput.prettyPrint(JsonOutput.toJson(instanceMap))}${NC}"
	
}

def updateInterfaces(Map instanceMap) {
	def primary = interfaces.find { it.primary_interface == true }
	def secondary = interfaces.find { it.primary_interface == false }
	
	println "Updating ${primary.id} to FALSE..."
	def DEMOTE_PRIMARY = "UPDATE compute_server_interface SET primary_interface = 0 WHERE id = ${primary.id}"
	executeQuery(DEMOTE_PRIMARY)

	println "Updating ${secondary.id} to TRUE..."
	def PROMOTE_TO_PRIMARY = "UPDATE compute_server_interface SET primary_interface = 1 WHERE id = ${secondary.id}"
	executeQuery(PROMOTE_TO_PRIMARY)
}


println "Executing getServerInterfaceMappings..."
getServerInterfaceMappings()

println "Executing getInterfaceData..."
getInterfaceData()

println "Executing getInstanceJSON()..."
getInstanceJSON()

println "Script execution completed."
