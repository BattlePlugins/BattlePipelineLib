def call(body) {
  pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  targetPath = ""
  if (pipelineParams.target_path) {
    targetPath = pipelineParams.target_path
  }

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

          dir(targetPath) {
            sh "mvn help:effective-pom -Doutput=target/effective-pom.pom"
          }
        }
      }
      stage('deploy release') {
        steps {
          script {
            configFileProvider([configFile(fileId: 'artifactory-settings', variable: 'SETTINGS')]) {
              sh "mvn deploy -s ${SETTINGS} -DskipTests -Dartifactory_url=https://artifactory.battleplugins.org/artifactory/"
            }
            dir(targetPath) {
              pom = readMavenPom file: "target/effective-pom.pom"
              archiveArtifacts artifacts: "${pom.build.directory}/${pom.build.finalName}", excludes: "original-*.jar", fingerprint: true
            }
          }
        }
      }
      stage('git release') {
        when { expression { BRANCH_NAME == 'master' } }
        steps {
          script {
            dir(targetPath){
              pom = readMavenPom file: "${targetPath}target/effective-pom.pom"
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
                  sh "curl -H \"Content-type: application/java-archive\" -H \"Authorization: token ${TOKEN}\" --upload-file ${pom.build.directory}/${pom.build.finalName}.jar ${json.upload_url}=${pom.build.finalName}.jar"
                } else {
                  echo "No upload_url found, is this a new release?"
                }
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