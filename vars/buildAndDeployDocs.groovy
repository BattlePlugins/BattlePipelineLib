def call(body) {
  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  pipeline {
    agent any
	triggers {
        cron('0 3 * * *')
	}
      }
    }
    options {
      quietPeriod(30)
    }
    stages {
      stage('install') {
        steps {
          echo "Installing Dependencies"
          sh 'yarn install'
        }
      }
      stage('configure') {
        steps {
          echo "Setting Variables"
		  withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {}
		  withCredentials([usernamePassword(credentialsId: 'github-login', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_PASSWORD')]) {}
		  sh "echo 'machine github.com login ${GITHUB_USER} password ${GITHUB_TOKEN}" > ~/.netrc'"
          sh "git config --global user.email '${GITHUB_USER}@users.noreply.github.com'"
          sh "git config --global user.name '${GITHUB_USER}'"
        }
      }
      stage('publish') {
        steps {
          echo "Building & Publishing"
          sh "GIT_USER=$GITHUB_USER yarn run publish-gh-pages"
        }
      }
    }
    post {
      always {
          deleteDir()
          sendStatusToDiscord repo: pipelineParams.repo
		   withCredentials([string(credentialsId: 'credential-id', variable: 'MY_SECRET')]) {
    echo MY_SECRET
}
      }
    }
  }
}