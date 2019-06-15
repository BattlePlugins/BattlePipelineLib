
def call (Map args){
  def artifactoryId = "Artifactory-1"
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
              "pattern": "target/[A-z]*-?[0-9.]*?.jar",
              "target": "libs-snapshot-local/",
              "regexp": "true"
            }
         ]
        }"""
  )
}