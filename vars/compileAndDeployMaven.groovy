def call(body) {
  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  pipeline {
    agent {
      docker {
        image 'maven:3-alpine'
        args '-v /root/.m2:/root/.m2'
      }
    }
    options {
      skipStagesAfterUnstable()
    }
    stages {
      stage('build') {
        steps {
          echo "Building ${pipelineParams.repo}"
          sh 'mvn -B -DskipTests clean package'
        }
      }
      stage('deliver') {
        steps {
          sh "mvn jar:jar install:install help:evaluate -Dexpression=project.name"
          deployToArtifactory repo: pipelineParams.repo
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