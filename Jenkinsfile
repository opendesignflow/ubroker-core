// TEA
node {
 
   //-- Github trigger
    properties([pipelineTriggers([[$class: 'GitHubPushTrigger']])])

    //-- JDK
    jdk = tool name: 'adopt-jdk11'
    env.JAVA_HOME = "${jdk}"

    //-- Maven
    def mvnHome = tool 'maven3'
    mavenOptions="-B -U -up"

  

    

  stage('Clean') {
    checkout scm
    sh "${mvnHome}/bin/mvn -version"
    sh "${mvnHome}/bin/mvn ${mavenOptions} clean"
  }

  stage('Build') {
    sh "${mvnHome}/bin/mvn ${mavenOptions}  compile test-compile"
  }

  stage('Test') {
    sh "${mvnHome}/bin/mvn ${mavenOptions}  -Dmaven.test.failure.ignore test"
    junit testResults:'**/target/surefire-reports/TEST-*.xml', allowEmptyResults: true 
  }

  if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
	  
	  stage('Deploy') {
      /*configFileProvider(
          [configFile(fileId: '040c946b-486d-4799-97a0-e92a4892e372', variable: 'MAVEN_SETTINGS')]) {
          //sh 'mvn -s $MAVEN_SETTINGS clean package'
          mavenOptions="$mavenOptions -s $MAVEN_SETTINGS"
  
          
      }*/
      sh "${mvnHome}/bin/mvn ${mavenOptions} -DskipTests=true deploy"
		  step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
	  }

    // Trigger sub builds on dev
    if (env.BRANCH_NAME == 'dev') {
      stage("Downstream") { 
        build job: '../ooxoo-core/dev', wait: false, propagate: false
      } 
      
    }

  } else {
	  
    stage('Package') {
        sh "${mvnHome}/bin/mvn ${mavenOptions} -Dmaven.test.failure.ignore package"
        step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
    }
	
  }  


}
