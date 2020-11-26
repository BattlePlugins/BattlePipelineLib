def call (Map args) {
  withCredentials([usernamePassword(credentialsId: 'github-login', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_PASS')]) {
    stdOut = sh returnStdout: true,
        script: "curl -H \"Content-type: application/json\" -u $GITHUB_USER:$GITHUB_PASS -d '{\n" +
            "\"tag_name\": \"${args.version}\",\n" +
            "\"target_commitish\": \"master\",\n" +
            "\"name\": \"v${args.version}\",\n" +
            "\"draft\": false,\n" +
            "\"prerelease\": false\n" +
            "}' https://api.github.com/repos/BattlePlugins/${args.repo}/releases"

    def json = readJSON text: stdOut
    if (json.upload_url) {
      sh "curl -H \"Content-type: application/java-archive\" -u $GITHUB_USER:$GITHUB_PASS --upload-file target/${args.final_name}.jar ${json.upload_url}=${args.final_name}.jar"
      return json.html_url
    } else {
      echo "No upload_url found, is this a new release?"
      return "https://github.com/BattlePlugins/${args.repo}/releases/${args.version}".toString()
    }
  }
}