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
      skipStagesAfterUnstable()
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
          sh "git config --global user.email "$GITHUB_USER@users.noreply.github.com""
          sh "git config --global user.name "$GITHUB_USER""
          sh "echo "machine github.com login $GITHUB_USER password $GITHUB_TOKEN" > ~/.netrc""
        }
      }
      stage('publish') {
        steps {
          echo "Building & Publishing"
          sh "yarn run publish-gh-pages"
        }
      }
    }
    post {
      always {
          sendStatusToDiscord repo: pipelineParams.repo
		  deleteDir()
      }
    }
  }
}