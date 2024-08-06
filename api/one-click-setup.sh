
APPLIANCE_URL="$morpheusApplianceUrlHere"
API_TOKEN="$morpheusApiTokenHere"

curl --request POST \
  --url $APPLIANCE_URL/api/tasks \
  --header "Authorization: Bearer $API_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{
  "task": {
    "visibility": "private",
    "taskType": {
      "code": "groovyTask"
    },
    "taskOptions": {},
    "executeTarget": "local",
    "retryable": false,
    "file": {
      "sourceType": "url",
      "contentUrl": "https://raw.githubusercontent.com/uthm4n/goomba-shuffler/main/api/updateInterfaces.groovy"
    },
    "name": "GoombaShuffle",
    "code": "network-interface-updater"
  }
}'
