
# goomba-shuffler

Purpose
---------
The goal of the Groovy Script is to swap the current secondary network interface the primary and vice versa by setting the **primary_interface** property to `0 (false)` or `1 (true)`

## Setup:

<details>
  <summary>Database</summary>

  1. SSH into a Morpheus application node
  2. Retrieve the database password from `/etc/morpheus/morpheus-secrets.json`
  3. Create a new Cypher called `secret/morpheus-mysql-datasource` that matches [dataSource.json](https://github.com/uthm4n/goomba-shuffler/blob/main/workflow/dataSource.json):

  ```json
  {
      "dataSource": {
        "url": "jdbc:mysql://127.0.0.1:3306/morpheus?autoReconnect=true&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true",
        "username": "morpheus",
        "password": "${REPLACE-ME}", 
        "driverClassName": "com.mysql.cj.jdbc.Driver"
      }
  }
  ```

  4. Replace the password in the JSON object above with your database password > Save the Cypher
  5. Create the Groovy Script task in Morpheus (Library > Automation > Tasks) - the script is located in the [workflow](https://github.com/uthm4n/goomba-shuffler/tree/main/workflow) folder in this repository.
  6. Attach to an operational or provisioning workflow
  7. Trigger on an **instance**

  **IMPORTANT:** take a backup of your appliance since every test scenario/case has not been handled. Please also test this in non-production, make any tweaks / enhancements and then use it. 

  **N.B.** The full config for your dataSource can be found in `/opt/morpheus/conf/application.yml` - ensure that the url, username, password, and driverClassName match the content found inside that file
</details>

<details>
  <summary>API</summary>

  1. Create a task in Morpheus with the code contained in the updateInterfaces.groovy file
  2. Trigger the task on a test instance and/or server
  3. Expected output should look something like this
</details>
