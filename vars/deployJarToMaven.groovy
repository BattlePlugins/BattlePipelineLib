def call(body) {
  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  pipeline {
    agent {
      docker {
        image 'maven:3-alpine'
      }
    }
    options {
      skipStagesAfterUnstable()
    }
    stages {
      stage('deploy') {
        steps {
          configFileProvider([configFile(fileId: 'artifactory-settings', variable: 'SETTINGS')]) {
            dir(pipelineParams.libPath) {
              for (def s : findFiles()) {
                sh "mvn install:install-file -Dfile=${s.path}"
              }
            }
          }
        }
      }
    }
    post {
      always {
        dir("server"){
          deleteDir()
        }
        sendStatusToDiscord repo: pipelineParams.repo
      }
    }
  }
}