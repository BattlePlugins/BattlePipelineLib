def call(body) {
  pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  targetPath = ""
  if (pipelineParams.target_path) {
    targetPath = pipelineParams.target_path
  }

  timeout(time: 30, unit: 'MINUTES') {
    pipeline {
      agent none
      options {
        skipStagesAfterUnstable()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '1'))
      }
      stages {
        stage('convert snapshot') {
          agent {
            docker {
              image 'maven:3-alpine'
            }
          }
          when { expression { BRANCH_NAME != 'master' } }
          steps {
            script {
              pom = readMavenPom file: 'pom.xml'
              sh "mvn versions:set -DnewVersion=${pom.version}-SNAPSHOT -f pom.xml"
            }
          }
        }
        stage('build') {
          agent {
            docker {
              image 'maven:3-alpine'
            }
          }
          steps {
            sh 'mvn -DskipTests -U clean package'

            dir(targetPath) {
              sh "mvn help:effective-pom -Doutput=target/effective-pom.pom"
            }
          }
        }
        stage('deploy') {
          agent {
            docker {
              image 'maven:3-alpine'
            }
          }
          steps {
            script {
              configFileProvider([configFile(fileId: 'artifactory-settings', variable: 'SETTINGS')]) {
                sh "mvn deploy -s ${SETTINGS} -DskipTests -Dartifactory_url=https://repo.battleplugins.org/artifactory/"
              }
              dir(targetPath) {
                pom = readMavenPom file: "target/effective-pom.pom"
                archiveArtifacts artifacts: "target/${pom.build.finalName}.jar", excludes: "original-*.jar", fingerprint: true
              }
            }
          }
        }
        stage('javadoc') {
          agent {
            docker {
              image 'maven:3-alpine'
            }
          }
          when { expression { BRANCH_NAME == 'master' } }
          steps {
            sh "mvn javadoc:javadoc"
            dir(targetPath) {
              publishHTML(target: [
                  allowMissing         : true,
                  alwaysLinkToLastBuild: true,
                  keepAll              : false,
                  reportDir            : 'target/site/apidocs',
                  reportFiles          : 'index.html',
                  reportName           : "Javadoc"
              ])
            }
          }
        }
        stage('git release') {
          agent any
          when { expression { BRANCH_NAME == 'master' } }
          steps {
            script {
              dir(targetPath){
                pom = readMavenPom file: "target/effective-pom.pom"
                html_url = uploadArtifactToGithub repo: pipelineParams.repo, version: pom.version, final_name: pom.build.finalName

                dir("site") {
                  git url: "https://github.com/BattlePlugins/BattleSite", branch: "master", credentialsId: "github-login"
                  dir("website/data") {
                    json = readJSON file: "plugins.json"
                    plugins = json.plugins
                    for (i = 0; i < plugins.size(); i++) {
                      plugin = plugins[i]
                      if (plugin.plugin.equals(pipelineParams.repo)) {
                        plugin.version = pom.version
                        plugin.updated = new Date().format('MM/dd/yyyy')
                        plugin.urlDownload = pom.distributionManagement.repository.url
                        plugin.githubRelease = html_url
                        plugin.jenkinsDownload = env.BUILD_URL

                        writeJSON json: json, file: "plugins.json", pretty: 4
                        break
                      }
                    }
                  }

                  sh "git add -A"
                  sh "git diff-index --quiet HEAD || git commit -m \"[AUTOMATED] Update plugins.json\" && git push origin master"
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
}
