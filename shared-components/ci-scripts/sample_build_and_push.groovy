@Library('eks_utilities@init-version') _
// a generic pod template can be used to create and render pods
import com.myapp.utils.PodTemplates 

def cloudSettings = [cloud: "nonprod-shared"]

podTemplates = new PodTemplates()

def setContainers = [kanikoEnabled: true, aquaEnabled: true, cdEnabled: true, sonarEnabled: true]

def branchPrefixReplaced = params.branch.replace("refs/heads/", "")


podTemplates.jnlpTemplate(cloudSettings, setContainers, params) {
  node(POD_LABEL) {
    try {
      stage("Clone the Git") {
        currentBuild.description = "Branch: ${branchPrefixReplaced}"
        container('jnlp') {
          gitOperations.clone(params.gitRepo,params.branch,"gitlab-user")
        }
      }
      //stage to scan 
      stage("trivvy scanning") {
        container('trivvy-scanner') {
          imageBuild.trivvyscan()
        }
        }
      def appList = buildController.appMapping(params.gitRepo)
      stage("Sonar Analysis") {
        //skipping if it is not qa
          if(branchPrefixReplaced == 'qa'){
            imageBuild.sonarScan(project,appList.sonar_project_name)
          }else{
            println "Skipping sonar scan, since the branch is not qa"
          }
        }
      imageBuildList =   appList.apps.collectEntries{ app ->
          ["Building image for ${app.app_name}" : {
            podTemplates.jnlpTemplate(cloudSettings, [kanikoEnabled: true], params) {
              node(POD_LABEL) {
                container('jnlp') {
                  gitOperations.clone(params.gitRepo,params.branch,"gitlab-user")
                }
                stage("build and push ${app.app_name}") { 
                  container('kaniko') {
                    //kaniko image build
                  }
                  container('crane') {
                    //crane push
  
                  }
                }
              }
            }
          }]
        }
      container('jnlp') {
        stage("update helm charts") {
          gitOperations.updateHelm(appList,"gitlab-user",params)
        }
      }
      if (!currentBuild.result){
      currentBuild.result = 'Success'
      }
    }catch(all){
      currentBuild.result = 'Failure'
    }
    finally{
      def appList = buildController.appMapping(params.gitRepo)
      def toEmails = appList."notify".join(' ')
      // notfiy with email plugins
    }
  }
}