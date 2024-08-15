// Function to clone a Git repository
def clone(gitUrl, branch, credentialsId, relativeTargetDir="./") {
    checkout([$class: 'GitSCM',
              branches: [
                [name: "${branch}"]
              ],
              doGenerateSubmoduleConfigurations: false,
              extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${relativeTargetDir}"]],
              submoduleCfg: [],
              userRemoteConfigs: [
                [credentialsId: credentialsId,
                  url: gitUrl
                ]
              ]
    ])
}

// Function to update Helm chart values
def updateHelm(appList, credentialsId, params) {
    lock('git-lock') {
        withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'gituser', passwordVariable: 'GIT_TOKEN')]) {
            script {
                def modifiedBranch
                sh "git config --global user.email 'platform.automation@mycompany.com'"
                sh "git config --global user.name 'platform.automation'"
                def gitUrl = appList."helm_repo"
                sh "git clone https://oauth2:${GIT_TOKEN}@${gitUrl}.git helm"

                appList."apps".each { appConfig ->
                    if (params.branch) {
                        modifiedBranch = params.branch.replaceAll('refs/heads/', '')
                        target_env = appConfig.branch_mapping[modifiedBranch]."target_env"
                    } else if (params.destination) {
                        target_env = params.destination.split('_')[0].toLowerCase()
                    }
                    def appName = appConfig."app_name"

                    dir("helm") {
                        try {
                            def fileContent = "${appName}/values-${params.project}-${target_env}.yaml"
                            def valuesfile = readYaml file: fileContent

                            if (params.branch) {
                                valuesfile['generic-application']['image']['tag'] = "$modifiedBranch-1.0.$currentBuild.number"
                            } else if (params.destination.contains('STAGE')) {
                                valuesfile['generic-application']['image']['tag'] = "stage-"+params.tag.split('-')[-1]
                            } else {
                                valuesfile['generic-application']['image']['tag'] = params.productionTag
                            }

                            writeYaml file: fileContent, data: valuesfile, overwrite: true
                            sh "git add * && git commit -m 'container image tag for ${appName}'"
                            sh "git push"
                        } catch (Exception e) {
                            echo "exception: ${e.message}"
                            throw e
                        }
                    }
                }
            }
        }
    }
}

// Function to create a Git tag in GitLab
def createTags(projectID, branch, credentialsId) {
    def modifiedBranch = branch.replaceAll('/', '')
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'gituser', passwordVariable: 'GIT_TOKEN')]) {
        def tagList = httpRequest(
            httpMode: 'POST',
            url: "https://gitlab.mycompany.io/api/v4/projects/${projectID}/repository/tags?tag_name=$modifiedBranch-1.0.$currentBuild.number&ref=$branch",
            customHeaders: [[name: 'PRIVATE-TOKEN', value: GIT_TOKEN]],
            contentType: 'APPLICATION_JSON'
        )
    }
}
