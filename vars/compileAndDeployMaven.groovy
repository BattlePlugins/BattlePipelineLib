def call(body) {
  pipelineParams = [:]
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
      stage('convert snapshot') {
        when { expression { BRANCH_NAME != 'master' } }
        steps {
          script {
            pom = readMavenPom file: 'pom.xml'
            sh "mvn versions:set -DnewVersion=${pom.version}-SNAPSHOT -f pom.xml"
          }
        }
      }
      stage('build') {
        steps {
          sh 'mvn -DskipTests -U clean package'
        }
      }
      stage('deploy release') {
        steps {
          configFileProvider([configFile(fileId: 'artifactory-settings', variable: 'SETTINGS')]) {
            sh "mvn deploy -s ${SETTINGS} -DskipTests -Dartifactory_url=https://artifactory.battleplugins.org/artifactory/"
          }
          archiveArtifacts artifacts: 'target/*.jar', excludes: "target/original-*.jar", fingerprint: true
        }
      }
      stage('git release') {
        when { expression { BRANCH_NAME == 'master' } }
        steps {
          script {
            sh "mvn help:effective-pom -Doutput=target/effective-pom.pom"
            pom = readMavenPom file: 'target/effective-pom.pom'
            withCredentials([string(credentialsId: 'github-token', variable: 'TOKEN')]) {
              stdOut = sh returnStdout: true,
                  script: "curl -H \"Content-type: application/json\" -H \"Authorization: token ${TOKEN}\" -d '{\n" +
                      "\"tag_name\": \"${pom.version}\",\n" +
                      "\"target_commitish\": \"master\",\n" +
                      "\"name\": \"v${pom.version}\",\n" +
                      "\"draft\": false,\n" +
                      "\"prerelease\": false\n" +
                      "}' https://api.github.com/repos/BattlePlugins/${pipelineParams.repo}/releases"

              json = readJSON text: stdOut
              if (json.upload_url) {
                sh "curl -H \"Content-type: application/java-archive\" -H \"Authorization: token ${TOKEN}\" --upload-file target/${pom.build.finalName}.jar ${json.upload_url}=${pom.build.finalName}.jar"
              } else {
                echo "No upload_url found, is this a new release?"
              }
            }
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