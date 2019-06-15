# BattlePipelineLib
## Usage
Create a file in the root directory of the repository called `Jenkinsfile` with contents:
```
library identifier: 'BattlePipelineLib@master', retriever: modernSCM(
    [$class: 'GitSCMSource',
     remote: 'https://github.com/BattlePlugins/BattlePipelineLib',
     credentialsId: 'github-login'])

compileAndDeployMaven {
    repo = 'REPO NAME HERE'
}
```