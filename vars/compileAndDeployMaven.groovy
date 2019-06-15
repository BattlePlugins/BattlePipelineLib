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
      stage('convert snapshot'){
        when { expression { BRANCH_NAME != 'master'} }
        steps {
          script {
            def pom = readMavenPom file: 'pom.xml'
            sh "mvn versions:set -DnewVersion=${pom.version}-SNAPSHOT -f pom.xml"
          }
        }
      }
      stage('build') {
        steps {
          sh 'mvn -DskipTests -U clean install'
        }
      }
      stage('deploy release') {
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