pipeline {
    agent any

    tools {
        maven 'Maven 3.9'
        jdk 'Temurin-25'
    }

    environment {
        DOCKER_HUB_CREDS                      = credentials('docker-hub-creds')
        SONAR_TOKEN                           = credentials('sonarqube-token')
        DOCKER_HOST                           = 'tcp://dind:2375'
        TESTCONTAINERS_HOST_OVERRIDE          = 'dind'
        DOCKER_REGISTRY                       = 'vitorff1234'
        IMAGE_TAG                             = "${env.BUILD_NUMBER}"
        SONAR_HOST_URL                        = 'http://erp-sonarqube:9000'
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh './mvnw clean verify -pl auth-service,cadastro-service,partner-service,billing-service -am --batch-mode --no-transfer-progress'
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh './mvnw sonar:sonar -pl auth-service,cadastro-service,partner-service,billing-service -am -Dsonar.token=${SONAR_TOKEN} --batch-mode --no-transfer-progress'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                sh '''
                    # gateway e registry herdam de spring-boot-starter-parent (fora do verify/Sonar dos apps);
                    # empacotamos aqui só para gerar o jar das imagens (sem rodar os testes deles).
                    ./mvnw clean package -pl gateway,registry -DskipTests --batch-mode --no-transfer-progress

                    for svc in auth-service cadastro-service partner-service billing-service gateway registry; do
                        docker build -t ${DOCKER_REGISTRY}/${svc}:${IMAGE_TAG} -t ${DOCKER_REGISTRY}/${svc}:latest -f ${svc}/Dockerfile ${svc}/
                    done
                    echo "${DOCKER_HUB_CREDS_PSW}" | docker login -u "${DOCKER_HUB_CREDS_USR}" --password-stdin
                    for svc in auth-service cadastro-service partner-service billing-service gateway registry; do
                        docker push ${DOCKER_REGISTRY}/${svc}:${IMAGE_TAG}
                        docker push ${DOCKER_REGISTRY}/${svc}:latest
                    done
                '''
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }
    }

    post {
        cleanup {
            sh 'for svc in auth-service cadastro-service partner-service billing-service gateway registry; do docker rmi ${DOCKER_REGISTRY}/${svc}:${IMAGE_TAG} || true; done'
            cleanWs()
        }
    }
}
