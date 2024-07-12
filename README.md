# network-interface-updater

### NOTE: THE FINAL WORKING SOLUTION IS IN THE `workflow/` FOLDER IN THIS REPOSITORY

## Setup:
1. SSH into a Morpheus application node
2. Retrieve the database password from `/etc/morpheus/morpheus-secrets.json`
3. Create a new Cypher called `secret/morpheus-mysql-datasource` that matches [dataSource.json](https://github.com/uthm4n/network-interface-updater/blob/main/workflow/dataSource.json):
```
{
    "dataSource": {
      "url": "jdbc:mysql://127.0.0.1:3306/morpheus?autoReconnect=true&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true",
      "username": "morpheus",
      "password": "${REPLACE-ME}", 
      "driverClassName": "com.mysql.cj.jdbc.Driver"
    }
}
```
3. Replace the password in the JSON object above with your database password > Save the Cypher
4. Create the Groovy Script tasks in Morpheus (Library > Automation > Tasks) - all of the scripts are in the [workflow](https://github.com/uthm4n/network-interface-updater/tree/main/workflow) folder in this repository.
 - **Note: when creating the scripts, the name and code need to be kept the same (just remove the '[1]', '[2]', etc...) e.g. the first task NAME would be `getCurrentInterfaces` and the task CODE would be `getCurrentInterfaces`**
5. Attach to an operational or provisioning workflow in the order I have specified
6. Trigger on an **instance**

**IMPORTANT:** take a backup of your appliance since every test scenario/case has not been handled 
**N.B.** The full config for your dataSource can be found in `/opt/morpheus/conf/application.yml` - ensure that the url, username, password, and driverClassName match the content found inside that file
