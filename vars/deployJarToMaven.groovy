def call(body) {
  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  pipeline {
    agent {
      docker {
        image 'maven:3-alpine'
        alwaysPull false
      }
    }
    options {
      skipStagesAfterUnstable()
    }
    stages {
      stage('setup') {
        steps {
          script {
            sh "mvn clean install"
          }
        }
      }
      stage('deploy'){
        steps {
          script {
            configFileProvider([configFile(fileId: 'artifactory-settings', variable: 'SETTINGS')]) {
              sh "mvn -s ${SETTINGS} deploy -Dartifactory_url=https://repo.battleplugins.org/artifactory/"
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