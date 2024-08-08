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

    println "Swapping interfaces in map..."
    instanceMap.instance.interfaces.each { network_interface ->
	def primaryInterfaceStatus = decode(network_interface.primary_interface)
	network_interface.primary_interface = primaryInterfaceStatus == '0' ? false : true
    }

    return result
}

def decode(String ENCODED_VAL, Boolean decodeNetworkInterfaces = false) {
    String ENCODED_VALUE = ENCODED_VAL
    if (decodeNetworkInterfaces) {
    	ENCODED_VALUE = ENCODED_VAL.split(":")[2]
	}
	
	byte[] ENCODED_BYTE_ARRAY = Base64.decoder.decode(ENCODED_VALUE)
	println "$ENCODED_BYTE_ARRAY"

	def DECODED_VAL = new String(ENCODED_BYTE_ARRAY, "UTF-8")
	println "${DECODED_VAL.toString()}"

    return "${DECODED_VAL.toString()}" ?: null
}

println "Executing getServerInterfaceMappings..."
getServerInterfaceMappings()

println "Executing getInterfaceData..."
getInterfaceData()

println "Executing getInstanceJSON()..."
getInstanceJSON()

println "Script execution completed."
