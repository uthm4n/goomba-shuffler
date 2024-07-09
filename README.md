# network-interface-updater

## Setup:
1. Ensure that `yq` is installed on the app node(s) - see [https://github.com/mikefarah/yq](https://github.com/mikefarah/yq?tab=readme-ov-file#install)
2. Create a Shell Script task in Morpheus and copy-paste the content of [workflow/shell-script-task-1.sh](https://github.com/uthm4n/network-interface-updater/blob/main/workflow/shell-script-task-1.sh) - the **name** and **code** of the task should be **getDatasourceDetails** and the target is **Local**
3. Create a Groovy Script task in Morpheus and copy-paste the content of [workflow/groovy-script-task-2.groovy](https://github.com/uthm4n/network-interface-updater/blob/main/workflow/groovy-script-task-2.groovy) // **target: Resource**
4. Link the tasks to a workflow (in the relevant order - as above)
5. **IMPORTANT:** take a backup of your appliance since every test scenario/case has not been tested
6. Trigger on an **instance**
