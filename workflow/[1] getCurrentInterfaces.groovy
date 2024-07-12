package com.morpheus

import groovy.json.*

def currentInterfaces = JsonOutput.toJson(instance.containers[0].server?.interfaces)

println "Server ID: ${instance.container?.serverId}"

println """
Current Network Interfaces [instance.containers[0].server?.interfaces]:
${currentInterfaces}

Index 0:
${instance.containers[0].server?.interfaces[0]}

Index 1:
${instance.containers[0].server?.interfaces[1]}

"""

return currentInterfaces