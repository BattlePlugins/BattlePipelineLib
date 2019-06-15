
def call (Map args){
  def artifactoryId = "Artifactory-1"
  dir("for_maven") {
    sh "cp ../target/${args.repo}.jar ."
  }

  rtServer(
      id: artifactoryId,
      url: "https://artifactory.battleplugins.org/artifactory/",
      credentialsId: 'artifactory-login'
  )

  rtUpload(
      serverId: artifactoryId,
      spec:
          """{
          "files": [
            {
              "pattern": "for_maven/*.jar",
              "target": "libs-snapshot-local/battleplugins"
            }
         ]
        }"""
  )

  deleteDir("for_maven")
}