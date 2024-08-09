package com.morpheus

import com.morpheus.MorpheusUtils
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions

SERVER_ID = "${server ? server.id : instance.container?.serverId}"

def API_CLIENT(String METHOD, String API_ENDPOINT, String API_KEY = null, Map body = null) {
    def client = new HttpApiClient()
    def requestOpts = new HttpApiClient.RequestOptions()
    try {
        API_KEY = morpheus.getApiAccessToken() ?: API_KEY
        requestOpts.headers = ["Authorization": "Bearer ${API_KEY}"]
        if (body) {
            requestOpts.body = body
        }
    } catch (Exception ex) {
        log.debug("ERROR OCCURRED:\r\n${ex}")
        throw ex
    }

    return client.callJsonApi(morpheus.applianceUrl, API_ENDPOINT, null, null, requestOpts, METHOD)
}

def getCurrentInterfaces(String SERVER_ID) {
    def getServer = API_CLIENT("GET", "/api/servers/$SERVER_ID", null, null)
    def results = [interfaces: [], data: []]
    if (getServer.success) {
        def interfaces = MorpheusUtils.getJson(getServer.data.server['interfaces'])

        interfaces.eachWithIndex { NETWORK_INTERFACE, index ->
            results.interfaces << [
                id: NETWORK_INTERFACE.getAt('id'),
                name: NETWORK_INTERFACE.getAt('name'),
                primaryInterface: NETWORK_INTERFACE.getAt('primaryInterface'),
                row: NETWORK_INTERFACE.getAt('primaryInterface') == true ? 0 : index,
                displayOrder: index
            ]
        }
        results.data = interfaces
    } else {
        log.debug("ERROR RETRIEVING SERVER $SERVER_ID FROM API...\r\n${getServer.errors}")
    }
    return results
}

def GOOMBA_SHUFFLE(Map SERVER_MAP) {
    def interfaces = SERVER_MAP.server.interfaces
    def PRIMARY = interfaces.findIndexOf { it.primaryInterface == true }
    def SECONDARY = interfaces.findIndexOf { it.primaryInterface == false }

    if (PRIMARY >= 0 && SECONDARY >= 0) {
        // Swap the primaryInterface properties and update row and displayOrder
        interfaces[PRIMARY].primaryInterface = false
        interfaces[PRIMARY].row = 1
        interfaces[PRIMARY].displayOrder = 1

        interfaces[SECONDARY].primaryInterface = true
        interfaces[SECONDARY].row = 0
        interfaces[SECONDARY].displayOrder = 0
    } else {
        log.debug("ERROR: Unable to find both primary and secondary interfaces to swap.")
    }
    
    return SERVER_MAP
}

def LOG(String description, Object payload) {
    println "================================== ${description} ======================================="
    println payload
    println ""
}

def generateServerInfo(Map SERVER_MAP) {
    def PRIMARY_ID = SERVER_MAP.server.interfaces.find { it.primaryInterface == true }?.id
    def NON_PRIMARY_IDS = SERVER_MAP.server.interfaces.findAll { it.primaryInterface == false }*.id

    def SERVER_INFO = """
    SERVER:
    ├── name: ${server.getAt('name')}
    ├── id: ${server.getAt('id')}
    └── network:
        └── interfaces: 
            ├── id: ${PRIMARY_ID} - PRIMARY: ░T░R░U░E░ ✅  
            ├── id: ${NON_PRIMARY_IDS.size() > 0 ? NON_PRIMARY_IDS[0] : "null"} - PRIMARY: ░F░A░L░S░E░ ❌
            └── id: ${NON_PRIMARY_IDS.size() > 1 ? NON_PRIMARY_IDS[1] : "null"} - PRIMARY: ░F░A░L░S░E░ ❌
	     
    data: ${SERVER_MAP}
    
    """
    
    return SERVER_INFO
}

def INTERFACE_RESULTS() {
    def NETWORK_INTERFACES = getCurrentInterfaces(SERVER_ID)?.interfaces
    def SERVER_MAP = [server: [interfaces: NETWORK_INTERFACES]]

    LOG("BEFORE UPDATE", generateServerInfo(SERVER_MAP))

    def UPDATED_SERVER_MAP = GOOMBA_SHUFFLE(SERVER_MAP)

    LOG("AFTER UPDATE", generateServerInfo(UPDATED_SERVER_MAP))

    def UPDATE_PAYLOAD = [
        server: [
            interfaces: UPDATED_SERVER_MAP.server.interfaces.collect { [
                id: it.id,
                primaryInterface: it.primaryInterface,
                row: it.row,
                displayOrder: it.displayOrder
            ] }
        ]
    ]

    def UPDATE_RESULTS = API_CLIENT("PUT", "/api/servers/$SERVER_ID", null, UPDATE_PAYLOAD)
    if (UPDATE_RESULTS.success) {
    	def final_results = UPDATE_RESULTS.data.server.interfaces
        println "================================== UPDATE_RESULTS =================================="
        final_results.each { network_interface ->
          println "ID: ${network_interface.id}, Name: ${network_interface.name}, Primary Interface: ${network_interface.primaryInterface ? ' ░T░R░U░E░' : '❌'}"
        }
        println ""
    	LOG("Interfaces successfully updated!", "✅ TRUE ✅")
    }
    else {
        log.debug("ERROR UPDATING SERVER $SERVER_ID: ${UPDATE_RESULTS.errors}")
        return "ERROR: Unable to update server interfaces."
    }

    LOG("**** DEBUG ****", UPDATE_RESULTS)
}

println "${INTERFACE_RESULTS()}"
