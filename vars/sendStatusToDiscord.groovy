def call (Map args){
  withCredentials([string(credentialsId: 'discord-webhook', variable: 'WEBHOOK_URL')]) {
    discordSend description: "${args.repo} build ${currentBuild.currentResult}", link: env.BUILD_URL, result: currentBuild.currentResult, unstable: false, title: JOB_NAME, webhookURL: WEBHOOK_URL
  }
}