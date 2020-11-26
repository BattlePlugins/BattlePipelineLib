def call(body) {
  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  pipeline {
    agent {
      docker {
        image 'node:current-buster'
        alwaysPull false
      }
    }
    options {
      skipStagesAfterUnstable()
      quietPeriod(30)
      buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '10'))
    }
    stages {
      stage('install') {
        steps {
          echo "Installing Dependencies"
          dir("website") {
            sh 'yarn install'
          }
        }
      }
      stage('configure') {
        steps {
          echo "Setting Variables"
          withCredentials([usernamePassword(credentialsId: 'github-login', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_PASSWORD')]) {
            sh "echo 'machine github.com login $GITHUB_USER password $GITHUB_PASSWORD > ~/.netrc'"
            sh "git config --global user.email '$GITHUB_USER@users.noreply.github.com'"
            sh "git config --global user.name '$GITHUB_USER'"
          }
        }
      }
      stage('publish') {
        steps {
          echo "Building & Publishing"
          withCredentials([usernamePassword(credentialsId: 'github-login', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_PASSWORD')]) {
            dir("website") {
              sh "GIT_USER=$GITHUB_USER yarn run publish-gh-pages"
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
