DATASOURCE=cat /opt/morpheus/conf/application.yml | yq eval '.environments.production | {"dataSource": {"url": .dataSource.url, "username": .dataSource.username, "password": .dataSource.password, "driverClassName": .dataSource.driverClassName}}' --output-format=json

if [ -n "$DATASOURCE" ]; then echo "$DATASOURCE"
exit 0
else exit 1
fi
