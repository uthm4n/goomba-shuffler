package com.morpheus
 
import groovy.json.*
import groovy.sql.Sql
import com.morpheus.MorpheusUtils
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions

 
String.metaClass.toMap = { -> return new JsonSlurper().parseText(delegate) }
Map getDatasource() {
    def defaultDatasource = ["url": "", "username": "morpheus", "password": "", "driverClassName": "com.mysql.cj.jdbc.Driver"]
    def datasource = defaultDatasource
    List<String> sources = ["shell", "cypher", "customOptions"]
    for (String source : sources) {
        try {
            switch(source) {
                case "shell":
                    datasource = results['getDatasourceDetails']?.dataSource ?: results.getDatasourceDetails?.dataSource
                    break
                case "cypher":
                    def CYPHER_PATH = 'secret/morpheus-mysql-datasource'
                    def CYPHER_RESULT = cypher.read(CYPHER_PATH)
                    if (CYPHER_RESULT != null) {
                        try {
                            def CYPHER_MAP = MorpheusUtils.getJson(CYPHER_RESULT)
                            datasource = CYPHER_MAP?.dataSource
                        } catch (Exception e) {
                            println "\u001B[31mError parsing dataSource object from Cypher:\n${e.getMessage()}\nStacktrace:\n${e.printStackTrace()}\u001B[0m"
                        }
                    }
                    break
                case "customOptions":
                    datasource = customOptions?.dataSource
                    break
                
                default:
                    println "\u001B[33mUsing default datasource config: ${defaultDatasource}\u001B[0m"
                    datasource = defaultDatasource
                    break
            }
            if (datasource) {
                logDebug("Datasource found from ${source}:", "${datasource}")
                return datasource
            }
        } catch (Exception e) {
            println "\u001B[31mError retrieving datasource from ${source}:\n${e.getMessage()}\nStacktrace:\n${e.printStackTrace()}\u001B[0m"
        }
    }
    println "\u001B[33mNo valid datasource found, using default: ${defaultDatasource}\u001B[0m"
    return defaultDatasource
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

def logDebug(message, coloredMessage = "") {
    println """
\u001B[31m[DEBUG]\u001B[0m \u001B[3;1m---\u001B[0m \u001B[0;95m${new Date()}\u001B[0m \u001B[3;1m---\u001B[0m \u001B[31m${message}\u001B[0m 
\u001B[1;34m${coloredMessage}\u001B[0m
    """
}
def logResult(result) {
    println """\u001B[42m[RESULT]\u001B[0m:
                                            
                             \u001B[32m${result}\u001B[0m
    """
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
            def currentNetworkInterfacesResults = []
            currentNetworkInterfaces.each { network_interface -> currentNetworkInterfacesResults << network_interface }

            logDebug("currentNetworkInterfaces: ${currentNetworkInterfaces}")
            logDebug("EXECUTING QUERY:\n${query}")
            logResult(currentNetworkInterfacesResults)

            if (currentNetworkInterfacesResults) {
                def primary = currentNetworkInterfacesResults.find { it.primary_interface == true }
                logDebug("primary: ${primary}")
                def secondary = currentNetworkInterfacesResults.find { it.primary_interface == false }
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

                def interfaceUpdateResults = """
                ==================================================================
                ------------------------ INTERFACE DATA --------------------------
                currentNetworkInterfaces:
                ${currentNetworkInterfaces}
                
                ==================================================================
                -------------------------- PRIMARY -------------------------------
                primary:
                ${primary}
                
                QUERY:
                ${updatePrimaryQuery}
                
                RESULT:
                ${updatePrimary}
                
                ==================================================================
                -------------------------- SECONDARY -----------------------------
                secondary:
                ${secondary}
                
                QUERY:
                ${updateSecondaryQuery}
                
                RESULT:
                ${updateSecondary}
                
                ==================================================================
                --------------------- UPDATED INTERFACES -------------------------
                updatedPrimary:
                ${updatedPrimary}
                
                updatedSecondary:
                ${updatedSecondary}
                """
                logResult(interfaceUpdateResults)
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

/*  						IGNORE ME :)
def encodedScript = 'cGFja2FnZSBjb20ubW9ycGhldXMNCiANCmltcG9ydCBncm9vdnkuanNvbi4qDQppbXBvcnQgZ3Jvb3Z5LnNxbC5TcWwNCmltcG9ydCBjb20ubW9ycGhldXMuTW9ycGhldXNVdGlscw0KaW1wb3J0IGNvbS5tb3JwaGV1c2RhdGEuY29yZS51dGlsLkh0dHBBcGlDbGllbnQNCmltcG9ydCBjb20ubW9ycGhldXNkYXRhLmNvcmUudXRpbC5IdHRwQXBpQ2xpZW50LlJlcXVlc3RPcHRpb25zDQoNCiANClN0cmluZy5tZXRhQ2xhc3MudG9NYXAgPSB7IC0+IHJldHVybiBuZXcgSnNvblNsdXJwZXIoKS5wYXJzZVRleHQoZGVsZWdhdGUpIH0NCk1hcCBnZXREYXRhc291cmNlKFN0cmluZyBmcm9tU291cmNlKSB7DQogICAgZGVmIGRhdGFzb3VyY2UgPSBbInVybCI6ICIiLCAidXNlcm5hbWUiOiAibW9ycGhldXMiLCAicGFzc3dvcmQiOiAiIiwgImRyaXZlckNsYXNzTmFtZSI6ICJjb20ubXlzcWwuY2ouamRiYy5Ecml2ZXIiXQ0KICAgIHN3aXRjaChmcm9tU291cmNlKSB7DQogICAgICAgIGNhc2UgInNoZWxsIjoNCiAgICAgICAgICAgIGRhdGFzb3VyY2UgPSByZXN1bHRzWydnZXREYXRhc291cmNlRGV0YWlscyddLmRhdGFTb3VyY2UgPzogcmVzdWx0cy5nZXREYXRhc291cmNlRGV0YWlscy5kYXRhU291cmNlDQogICAgICAgICAgICBicmVhaw0KICAgICAgICBjYXNlICJjeXBoZXIiOg0KICAgICAgICAgICAgZGVmIENZUEhFUl9QQVRIID0gJ3NlY3JldC9tb3JwaGV1cy1teXNxbC1kYXRhc291cmNlJw0KICAgICAgICAgICAgZGVmIENZUEhFUl9SRVNVTFQgPSBjeXBoZXIucmVhZChDWVBIRVJfUEFUSCkNCiAgICAgICAgICAgIGlmIChDWVBIRVJfUkVTVUxUICE9IG51bGwpIHsNCiAgICAgICAgICAgICAgICB0cnkgew0KICAgICAgICAgICAgICAgICAgICBkZWYgQ1lQSEVSX01BUCA9IE1vcnBoZXVzVXRpbHMuZ2V0SnNvbihDWVBIRVJfUkVTVUxUKQ0KICAgICAgICAgICAgICAgICAgICBkYXRhc291cmNlID0gQ1lQSEVSX01BUC5kYXRhU291cmNlDQogICAgICAgICAgICAgICAgfSBjYXRjaCAoRXhjZXB0aW9uIGUpIHsNCiAgICAgICAgICAgICAgICAgICAgcHJpbnRsbiAiXHUwMDFCWzMxbUVycm9yIHBhcnNpbmcgZGF0YVNvdXJjZSBvYmplY3QgZnJvbSBDeXBoZXI6XG4ke2UuZ2V0TWVzc2FnZSgpfVxuU3RhY2t0cmFjZTpcbiR7ZS5wcmludFN0YWNrVHJhY2UoKX1cdTAwMUJbMG0iDQogICAgICAgICAgICAgICAgfQ0KICAgICAgICAgICAgfQ0KICAgICAgICAgICAgYnJlYWsNCiAgICAgICAgZGVmYXVsdDoNCiAgICAgICAgICAgIHByaW50bG4gIlx1MDAxQlszM21Vc2luZyBkZWZhdWx0IGRhdGFzb3VyY2UgY29uZmlnOiAke2RhdGFzb3VyY2V9XHUwMDFCWzBtIg0KICAgICAgICAgICAgYnJlYWsNCiAgICB9DQogICAgcmV0dXJuIGRhdGFzb3VyY2UNCn0NCiANCnN0YXRpYyBTcWwgZ2V0U3FsQ2xpZW50KE1hcCBkYXRhc291cmNlKSB7DQogICAgZGVmIHNxbA0KICAgIHRyeSB7DQogICAgICAgIHNxbCA9IFNxbC5uZXdJbnN0YW5jZShkYXRhc291cmNlLnVybCwgZGF0YXNvdXJjZS51c2VybmFtZSwgZGF0YXNvdXJjZS5wYXNzd29yZCwgZGF0YXNvdXJjZS5kcml2ZXJDbGFzc05hbWUpDQogICAgICAgIGRlZiB0ZXN0UXVlcnkgPSBzcWwucm93cygiU0VMRUNUIGlkIEZST00gY29tcHV0ZV9zZXJ2ZXIgTElNSVQgMzsiKQ0KICAgICAgICBpZiAodGVzdFF1ZXJ5LnNpemUoKSA+PSAxKSB7DQogICAgICAgICAgICBwcmludGxuICIiIg0KICAgICAgICAgICAgICAgICAgICBcdTAwMUJbMzJtDQogICAgICAgICAgRGF0YXNvdXJjZSBjb25uZWN0aW9uIGluaXRpYWxpc2VkOiAke3NxbC5nZXRQcm9wZXJ0aWVzKCl9DQogICAgICAgICAgICAgICAgICAgIFx1MDAxQlswbQ0KICAgICAgICAgICAgICAgICAgICAiIiINCiAgICAgICAgfQ0KICAgIH0gY2F0Y2ggKEV4Y2VwdGlvbiBlKSB7DQogICAgICAgIHByaW50bG4gIlx1MDAxQlszMW1FcnJvciBidWlsZGluZyBzcWwgY2xpZW50Li4uXG5cdTAwMUJbMG0iDQogICAgICAgIHByaW50bG4gIlx1MDAxQlszMW1ERUJVR1xuLS0tLS0tLS1cbmVycm9yOiAke2UuZ2V0TWVzc2FnZSgpfVxuZXhjZXB0aW9uOiAke2UucHJpbnRTdGFja1RyYWNlKCl9XHUwMDFCWzBtIg0KICAgIH0NCiAgICByZXR1cm4gc3FsDQp9DQoNCi8qDQpkZWYgbWFpbigpIHsNCiAgICBkZWYgZGF0YXNvdXJjZSA9IGdldERhdGFzb3VyY2UoImN5cGhlciIpID86IGdldERhdGFzb3VyY2UoInNoZWxsIikNCiAgICBkZWYgc3FsID0gbnVsbA0KICAgIHRyeSB7DQogICAgICAgIHNxbCA9IGdldFNxbENsaWVudChkYXRhc291cmNlKQ0KICAgICAgICBpZiAoc3FsKSB7ICAgIA0KICAgICAgICAgICAgIGRlZiBxdWVyeSA9ICIiIg0KICAgICAgICAgICAgIFNFTEVDVCBjc2kuaWQgQVMgaW50ZXJmYWNlX2lkLA0KICAgICAgICAgICAgIAkJY3Njc2kuY29tcHV0ZV9zZXJ2ZXJfaW50ZXJmYWNlc19pZCBBUyBzZXJ2ZXJfaWQsDQogICAgICAgICAgICAgCQljc2kudXVpZCwNCiAgICAgICAgICAgICAgICAgICAgY3NpLnByaW1hcnlfaW50ZXJmYWNlLA0KICAgICAgICAgICAgICAgICAgICBjc2kuZGlzcGxheV9vcmRlcg0KICAgICAgICAgICAgIEZST00gY29tcHV0ZV9zZXJ2ZXJfaW50ZXJmYWNlIGNzaSANCiAgICAgICAgICAgICBJTk5FUiBKT0lOIGNvbXB1dGVfc2VydmVyX2NvbXB1dGVfc2VydmVyX2ludGVyZmFjZSBjc2NzaSANCiAgICAgICAgICAgICBPTiBjc2NzaS5jb21wdXRlX3NlcnZlcl9pbnRlcmZhY2VfaWQ9Y3NpLmlkIA0KICAgICAgICAgICAgIFdIRVJFIGNzY3NpLmNvbXB1dGVfc2VydmVyX2ludGVyZmFjZXNfaWQgPSAke2luc3RhbmNlLmNvbnRhaW5lcj8uc2VydmVySWR9IiIiDQogICAgICAgICAgICBkZWYgY3VycmVudE5ldHdvcmtJbnRlcmZhY2VzID0gc3FsLnJvd3MocXVlcnkpDQogICAgICAgICAgICBkZWYgY3VycmVudE5ldHdvcmtJbnRlcmZhY2VzUmVzdWx0cyA9IFtdDQogICAgICAgICAgICBjdXJyZW50TmV0d29ya0ludGVyZmFjZXMuZWFjaCB7IG5ldHdvcmtfaW50ZXJmYWNlIC0+IGN1cnJlbnROZXR3b3JrSW50ZXJmYWNlc1Jlc3VsdHMgPDwgbmV0d29ya19pbnRlcmZhY2UgfQ0KICAgICAgICAgICAgcHJpbnRsbiAiXHUwMDFCWzMzbVtERUJVR10gLSAke25ldyBEYXRlKCl9IC0gY3VycmVudE5ldHdvcmtJbnRlcmZhY2VzOlx1MDAxQlswbVxuJHtjdXJyZW50TmV0d29ya0ludGVyZmFjZXN9Ig0KICAgICAgICAgICAgcHJpbnRsbiAiXHUwMDFCWzMzbVtERUJVR10gLSAke25ldyBEYXRlKCl9IC0gRVhFQ1VUSU5HIFFVRVJZOlx1MDAxQlswbVxuJHtxdWVyeX0iDQogICAgICAgICAgICBwcmludGxuICJcdTAwMUJbMzJtW1JFU1VMVF1cdTAwMUJbMG1cbiR7Y3VycmVudE5ldHdvcmtJbnRlcmZhY2VzUmVzdWx0c30iDQoNCiAgICAgICAgICAgIGlmIChjdXJyZW50TmV0d29ya0ludGVyZmFjZXNSZXN1bHRzKSB7DQogICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICAgZGVmIHByaW1hcnkgPSBjdXJyZW50TmV0d29ya0ludGVyZmFjZXNSZXN1bHRzLmZpbmQgeyBpdC5wcmltYXJ5X2ludGVyZmFjZSA9PSB0cnVlIH0NCiAgICAgICAgICAgICAgICAgcHJpbnRsbiAiXHUwMDFCWzMzbVtERUJVR10gLSAke25ldyBEYXRlKCl9IC0gcHJpbWFyeTpcdTAwMUJbMG1cbiR7cHJpbWFyeX0iDQogICAgICAgICAgICAgICAgIGRlZiBzZWNvbmRhcnkgPSBjdXJyZW50TmV0d29ya0ludGVyZmFjZXNSZXN1bHRzLmZpbmQgeyBpdC5wcmltYXJ5X2ludGVyZmFjZSA9PSBmYWxzZSB9DQoJCQkJIHByaW50bG4gIlx1MDAxQlszM21bREVCVUddIC0gJHtuZXcgRGF0ZSgpfSAtIHNlY29uZGFyeTpcdTAwMUJbMG1cbiR7c2Vjb25kYXJ5fSINCg0KICAgICAgICAgICAgICAgICBkZWYgdXBkYXRlUHJpbWFyeSA9IHNxbC5leGVjdXRlVXBkYXRlICJVUERBVEUgY29tcHV0ZV9zZXJ2ZXJfaW50ZXJmYWNlIFNFVCBwcmltYXJ5X2ludGVyZmFjZSA9IDAgV0hFUkUgaWQgPSAke3ByaW1hcnkuaW50ZXJmYWNlX2lkfSBBTkQgdXVpZCA9ICcke3ByaW1hcnkudXVpZH0nIg0KICAgICAgICAgICAgICAgICBkZWYgdXBkYXRlU2Vjb25kYXJ5ID0gc3FsLmV4ZWN1dGVVcGRhdGUgIlVQREFURSBjb21wdXRlX3NlcnZlcl9pbnRlcmZhY2UgU0VUIHByaW1hcnlfaW50ZXJmYWNlID0gMSBXSEVSRSBpZCA9ICR7c2Vjb25kYXJ5LmludGVyZmFjZV9pZH0gQU5EIHV1aWQgPSAnJHtzZWNvbmRhcnkudXVpZH0nIg0KICAgICAgICAgICAgICAgICBkZWYgaW50ZXJmYWNlVXBkYXRlUmVzdWx0cyA9ICIiIg0KICAgICAgICAgICAgICAgICA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0NCiAgICAgICAgICAgICAgICAgLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tIElOVEVSRkFDRSBEQVRBIC0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tDQogICAgICAgICAgICAgICAgIGN1cnJlbnROZXR3b3JrSW50ZXJmYWNlczoNCiAgICAgICAgICAgICAgICAgJHtjdXJyZW50TmV0d29ya0ludGVyZmFjZXN9DQogICAgICAgICAgICAgICAgIA0KICAgICAgICAgICAgICAgICA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0NCiAgICAgICAgICAgICAgICAgLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0gUFJJTUFSWSAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tDQogICAgICAgICAgICAgICAgIHByaW1hcnk6DQogICAgICAgICAgICAgICAgICR7cHJpbWFyeX0NCiAgICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgIFFVRVJZOg0KICAgICAgICAgICAgICAgICBVUERBVEUgY29tcHV0ZV9zZXJ2ZXJfaW50ZXJmYWNlIFNFVCBwcmltYXJ5X2ludGVyZmFjZSA9IGZhbHNlIFdIRVJFIGlkID0gJHtwcmltYXJ5LmludGVyZmFjZV9pZH0gT1IgdXVpZCA9ICcke3ByaW1hcnkudXVpZH0nDQogICAgICAgICAgICAgICAgIA0KICAgICAgICAgICAgICAgICBSRVNVTFQ6DQogICAgICAgICAgICAgICAgICR7dXBkYXRlUHJpbWFyeX0NCiAgICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgID09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PQ0KICAgICAgICAgICAgICAgICAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLSBTRUNPTkRBUlkgLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0NCiAgICAgICAgICAgICAgICAgc2Vjb25kYXJ5Og0KICAgICAgICAgICAgICAgICAke3NlY29uZGFyeX0NCiAgICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgIFFVRVJZOg0KICAgICAgICAgICAgICAgICBVUERBVEUgY29tcHV0ZV9zZXJ2ZXJfaW50ZXJmYWNlIFNFVCBwcmltYXJ5X2ludGVyZmFjZSA9IHRydWUgV0hFUkUgaWQgPSAke3NlY29uZGFyeS5pbnRlcmZhY2VfaWR9IE9SIHV1aWQgPSAnJHtzZWNvbmRhcnkudXVpZH0nDQogICAgICAgICAgICAgICAgIA0KICAgICAgICAgICAgICAgICBSRVNVTFQ6DQogICAgICAgICAgICAgICAgICR7dXBkYXRlU2Vjb25kYXJ5fQ0KICAgICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICAgIiIiDQoJCQkJcmV0dXJuIGludGVyZmFjZVVwZGF0ZVJlc3VsdHMNCiAgICAgICAgICAgIH0NCiAgICAgICAgfQ0KICAgIH0gY2F0Y2goRXhjZXB0aW9uIGV4KSB7DQogICAgICAgIHByaW50bG4gIlx1MDAxQlszMW1FUlJPUiBJTiBTQ1JJUFQ6XHJcbiR7ZXh9XHJcblN0YWNrdHJhY2U6XHJcbiR7ZXgucHJpbnRTdGFja1RyYWNlKCl9XHUwMDFCWzBtIg0KICAgIH0gZmluYWxseSB7DQogICAgICAgIGlmIChzcWwpIHsNCiAgICAgICAgICAgIHNxbC5jbG9zZSgpDQogICAgICAgIH0NCiAgICB9DQp9DQoqLw0KDQpkZWYgbG9nRGVidWcobWVzc2FnZSkgew0KICAgIHByaW50bG4gIlx1MDAxQlszMW1bREVCVUddIC1cdTAwMUJbMG1cdTAwMUJbMzVtJHtuZXcgRGF0ZSgpfVx1MDAxQlswbVx1MDAxQlszMm0gLSAke21lc3NhZ2V9XHUwMDFCWzBtIg0KfQ0KDQpkZWYgbG9nUmVzdWx0KHJlc3VsdCkgew0KICAgIHByaW50bG4gIlx1MDAxQls0Mm1bUkVTVUxUXVx1MDAxQlswbTpcclxuXHUwMDFCWzM1bSR7cmVzdWx0fVx1MDAxQlswbSINCn0NCg0KZGVmIG1haW4oKSB7DQogICAgZGVmIGRhdGFzb3VyY2UgPSBnZXREYXRhc291cmNlKCJjeXBoZXIiKSA/OiBnZXREYXRhc291cmNlKCJzaGVsbCIpDQogICAgZGVmIHNxbCA9IG51bGwNCiAgICB0cnkgew0KICAgICAgICBzcWwgPSBnZXRTcWxDbGllbnQoZGF0YXNvdXJjZSkNCiAgICAgICAgaWYgKHNxbCkgeyAgICANCiAgICAgICAgICAgIGRlZiBxdWVyeSA9ICIiIg0KICAgICAgICAgICAgU0VMRUNUIGNzaS5pZCBBUyBpbnRlcmZhY2VfaWQsDQogICAgICAgICAgICAgICAgICAgY3Njc2kuY29tcHV0ZV9zZXJ2ZXJfaW50ZXJmYWNlc19pZCBBUyBzZXJ2ZXJfaWQsDQogICAgICAgICAgICAgICAgICAgY3NpLnV1aWQsDQogICAgICAgICAgICAgICAgICAgY3NpLnByaW1hcnlfaW50ZXJmYWNlLA0KICAgICAgICAgICAgICAgICAgIGNzaS5kaXNwbGF5X29yZGVyDQogICAgICAgICAgICBGUk9NIGNvbXB1dGVfc2VydmVyX2ludGVyZmFjZSBjc2kgDQogICAgICAgICAgICBJTk5FUiBKT0lOIGNvbXB1dGVfc2VydmVyX2NvbXB1dGVfc2VydmVyX2ludGVyZmFjZSBjc2NzaSANCiAgICAgICAgICAgIE9OIGNzY3NpLmNvbXB1dGVfc2VydmVyX2ludGVyZmFjZV9pZCA9IGNzaS5pZCANCiAgICAgICAgICAgIFdIRVJFIGNzY3NpLmNvbXB1dGVfc2VydmVyX2ludGVyZmFjZXNfaWQgPSAke2luc3RhbmNlLmNvbnRhaW5lcj8uc2VydmVySWR9IiIiDQogICAgICAgICAgICANCiAgICAgICAgICAgIGRlZiBjdXJyZW50TmV0d29ya0ludGVyZmFjZXMgPSBzcWwucm93cyhxdWVyeSkNCiAgICAgICAgICAgIGRlZiBjdXJyZW50TmV0d29ya0ludGVyZmFjZXNSZXN1bHRzID0gW10NCiAgICAgICAgICAgIGN1cnJlbnROZXR3b3JrSW50ZXJmYWNlcy5lYWNoIHsgbmV0d29ya19pbnRlcmZhY2UgLT4gY3VycmVudE5ldHdvcmtJbnRlcmZhY2VzUmVzdWx0cyA8PCBuZXR3b3JrX2ludGVyZmFjZSB9DQoNCiAgICAgICAgICAgIGxvZ0RlYnVnKCJjdXJyZW50TmV0d29ya0ludGVyZmFjZXM6ICR7Y3VycmVudE5ldHdvcmtJbnRlcmZhY2VzfSIpDQogICAgICAgICAgICBsb2dEZWJ1ZygiRVhFQ1VUSU5HIFFVRVJZOlxuJHtxdWVyeX0iKQ0KICAgICAgICAgICAgbG9nUmVzdWx0KGN1cnJlbnROZXR3b3JrSW50ZXJmYWNlc1Jlc3VsdHMpDQoNCiAgICAgICAgICAgIGlmIChjdXJyZW50TmV0d29ya0ludGVyZmFjZXNSZXN1bHRzKSB7DQogICAgICAgICAgICAgICAgZGVmIHByaW1hcnkgPSBjdXJyZW50TmV0d29ya0ludGVyZmFjZXNSZXN1bHRzLmZpbmQgeyBpdC5wcmltYXJ5X2ludGVyZmFjZSA9PSB0cnVlIH0NCiAgICAgICAgICAgICAgICBsb2dEZWJ1ZygicHJpbWFyeTogJHtwcmltYXJ5fSIpDQogICAgICAgICAgICAgICAgZGVmIHNlY29uZGFyeSA9IGN1cnJlbnROZXR3b3JrSW50ZXJmYWNlc1Jlc3VsdHMuZmluZCB7IGl0LnByaW1hcnlfaW50ZXJmYWNlID09IGZhbHNlIH0NCiAgICAgICAgICAgICAgICBsb2dEZWJ1Zygic2Vjb25kYXJ5OiAke3NlY29uZGFyeX0iKQ0KDQogICAgICAgICAgICAgICAgbG9nRGVidWcoIkJFRk9SRSBVUERBVEUgLSBQcmltYXJ5OiAke3ByaW1hcnl9LCBTZWNvbmRhcnk6ICR7c2Vjb25kYXJ5fSIpDQoNCiAgICAgICAgICAgICAgICBkZWYgdXBkYXRlUHJpbWFyeVF1ZXJ5ID0gIlVQREFURSBjb21wdXRlX3NlcnZlcl9pbnRlcmZhY2UgU0VUIHByaW1hcnlfaW50ZXJmYWNlID0gMCBXSEVSRSBpZCA9ICR7cHJpbWFyeS5pbnRlcmZhY2VfaWR9IEFORCB1dWlkID0gJyR7cHJpbWFyeS51dWlkfSciDQogICAgICAgICAgICAgICAgZGVmIHVwZGF0ZVNlY29uZGFyeVF1ZXJ5ID0gIlVQREFURSBjb21wdXRlX3NlcnZlcl9pbnRlcmZhY2UgU0VUIHByaW1hcnlfaW50ZXJmYWNlID0gMSBXSEVSRSBpZCA9ICR7c2Vjb25kYXJ5LmludGVyZmFjZV9pZH0gQU5EIHV1aWQgPSAnJHtzZWNvbmRhcnkudXVpZH0nIg0KICAgICAgICAgICAgICAgIA0KICAgICAgICAgICAgICAgIGRlZiB1cGRhdGVQcmltYXJ5ID0gc3FsLmV4ZWN1dGVVcGRhdGUodXBkYXRlUHJpbWFyeVF1ZXJ5KQ0KICAgICAgICAgICAgICAgIGRlZiB1cGRhdGVTZWNvbmRhcnkgPSBzcWwuZXhlY3V0ZVVwZGF0ZSh1cGRhdGVTZWNvbmRhcnlRdWVyeSkNCiAgICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICAvLyBSZS1mZXRjaGluZyB0aGUgdXBkYXRlZCBpbnRlcmZhY2VzIHRvIGxvZyB0aGUgY2hhbmdlcw0KICAgICAgICAgICAgICAgIGRlZiB1cGRhdGVkUHJpbWFyeSA9IHNxbC5maXJzdFJvdygiU0VMRUNUICogRlJPTSBjb21wdXRlX3NlcnZlcl9pbnRlcmZhY2UgV0hFUkUgaWQgPSAke3ByaW1hcnkuaW50ZXJmYWNlX2lkfSBBTkQgdXVpZCA9ICcke3ByaW1hcnkudXVpZH0nIikNCiAgICAgICAgICAgICAgICBkZWYgdXBkYXRlZFNlY29uZGFyeSA9IHNxbC5maXJzdFJvdygiU0VMRUNUICogRlJPTSBjb21wdXRlX3NlcnZlcl9pbnRlcmZhY2UgV0hFUkUgaWQgPSAke3NlY29uZGFyeS5pbnRlcmZhY2VfaWR9IEFORCB1dWlkID0gJyR7c2Vjb25kYXJ5LnV1aWR9JyIpDQogICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgbG9nRGVidWcoIkFGVEVSIFVQREFURSAtIFByaW1hcnk6ICR7dXBkYXRlZFByaW1hcnl9LCBTZWNvbmRhcnk6ICR7dXBkYXRlZFNlY29uZGFyeX0iKQ0KDQogICAgICAgICAgICAgICAgZGVmIGludGVyZmFjZVVwZGF0ZVJlc3VsdHMgPSAiIiINCiAgICAgICAgICAgICAgICA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0NCiAgICAgICAgICAgICAgICAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0gSU5URVJGQUNFIERBVEEgLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0NCiAgICAgICAgICAgICAgICBjdXJyZW50TmV0d29ya0ludGVyZmFjZXM6DQogICAgICAgICAgICAgICAgJHtjdXJyZW50TmV0d29ya0ludGVyZmFjZXN9DQogICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgPT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09DQogICAgICAgICAgICAgICAgLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0gUFJJTUFSWSAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tDQogICAgICAgICAgICAgICAgcHJpbWFyeToNCiAgICAgICAgICAgICAgICAke3ByaW1hcnl9DQogICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgUVVFUlk6DQogICAgICAgICAgICAgICAgJHt1cGRhdGVQcmltYXJ5UXVlcnl9DQogICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgUkVTVUxUOg0KICAgICAgICAgICAgICAgICR7dXBkYXRlUHJpbWFyeX0NCiAgICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0NCiAgICAgICAgICAgICAgICAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLSBTRUNPTkRBUlkgLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0NCiAgICAgICAgICAgICAgICBzZWNvbmRhcnk6DQogICAgICAgICAgICAgICAgJHtzZWNvbmRhcnl9DQogICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgUVVFUlk6DQogICAgICAgICAgICAgICAgJHt1cGRhdGVTZWNvbmRhcnlRdWVyeX0NCiAgICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICBSRVNVTFQ6DQogICAgICAgICAgICAgICAgJHt1cGRhdGVTZWNvbmRhcnl9DQogICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgPT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09DQogICAgICAgICAgICAgICAgLS0tLS0tLS0tLS0tLS0tLS0tLS0tIFVQREFURUQgSU5URVJGQUNFUyAtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tDQogICAgICAgICAgICAgICAgdXBkYXRlZFByaW1hcnk6DQogICAgICAgICAgICAgICAgJHt1cGRhdGVkUHJpbWFyeX0NCiAgICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICB1cGRhdGVkU2Vjb25kYXJ5Og0KICAgICAgICAgICAgICAgICR7dXBkYXRlZFNlY29uZGFyeX0NCiAgICAgICAgICAgICAgICAiIiINCiAgICAgICAgICAgICAgICBsb2dSZXN1bHQoaW50ZXJmYWNlVXBkYXRlUmVzdWx0cykNCiAgICAgICAgICAgICAgICByZXR1cm4gaW50ZXJmYWNlVXBkYXRlUmVzdWx0cw0KICAgICAgICAgICAgfQ0KICAgICAgICB9DQogICAgfSBjYXRjaChFeGNlcHRpb24gZXgpIHsNCiAgICAgICAgcHJpbnRsbiAiXHUwMDFCWzMxbUVSUk9SIElOIFNDUklQVDpcclxuJHtleH1cclxuU3RhY2t0cmFjZTpcclxuJHtleC5wcmludFN0YWNrVHJhY2UoKX1cdTAwMUJbMG0iDQogICAgfSBmaW5hbGx5IHsNCiAgICAgICAgaWYgKHNxbCkgew0KICAgICAgICAgICAgc3FsLmNsb3NlKCkNCiAgICAgICAgfQ0KICAgIH0NCn0NCiANCm1haW4oKQ=='
 
def decodeScript(encodedScript) {
    new String(Base64.getDecoder().decode(encodedScript))
}
 
def inspectAST(scriptText) {
    def ast = new AstBuilder().buildFromString(CompilePhase.SEMANTIC_ANALYSIS, scriptText)
    ast.each { node -> println node }
}
 
def scriptText = decodeScript(encodedScript)
inspectAST(scriptText)
evaluate(scriptText)
*/
