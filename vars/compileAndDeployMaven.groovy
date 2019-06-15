def call(body) {
  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  pipeline {
    agent {
      docker {
        image 'maven:3-alpine'
        args '-v /home/cwdmedias/.m2:/root/.m2'
      }
    }
    options {
      skipStagesAfterUnstable()
    }
    stages {
      stage('install') {
        steps {
          echo "Building ${pipelineParams.repo}"
          sh 'mvn -U clean install'
        }
      }
      stage('deliver') {
        steps {
          configFileProvider([configFile(fileId: 'artifactory-settings', variable: 'SETTINGS')]) {
            sh "mvn deploy -s ${SETTINGS} -DskipTests -Dartifactory_url=https://artifactory.battleplugins.org/artifactory/"
          }
        }
      }
    }
    post {
      always {
        sendStatusToDiscord repo: pipelineParams.repo
      }
    }
  }
}