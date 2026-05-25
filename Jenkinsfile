pipeline {
    agent any
    
    environment {
        APP_NAME = 'jvm-demo'
        DOCKER_REGISTRY = credentials('docker-registry-url')
        DOCKER_USERNAME = credentials('docker-username')
        DOCKER_PASSWORD = credentials('docker-password')
        GIT_URL = 'https://github.com/97460200/jvm-demo.git'
        BRANCH_NAME = 'main'
        MAVEN_OPTS = '-Xmx1024m'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }
    
    parameters {
        booleanParam(name: 'RUN_SONAR', defaultValue: true, description: '是否运行 SonarQube 扫描')
        booleanParam(name: 'DEPLOY_TO_DEV', defaultValue: true, description: '是否部署到开发环境')
        booleanParam(name: 'DEPLOY_TO_PROD', defaultValue: false, description: '是否部署到生产环境（需要人工确认）')
    }
    
    stages {
        stage('Checkout Code') {
            steps {
                echo '检查代码...'
                checkout scm
            }
        }
        
        stage('Initialize') {
            steps {
                echo '初始化环境...'
                sh 'java -version'
                sh 'mvn -version'
                sh 'docker -v'
            }
        }
        
        stage('Static Code Analysis') {
            steps {
                script {
                    if (params.RUN_SONAR) {
                        echo '运行静态代码分析...'
                        withSonarQubeEnv('SonarQube') {
                            sh 'mvn sonar:sonar'
                        }
                        timeout(time: 1, unit: 'HOURS') {
                            waitForQualityGate abortPipeline: true
                        }
                    } else {
                        echo '跳过 SonarQube 扫描'
                    }
                }
            }
        }
        
        stage('Build & Test') {
            steps {
                echo '编译并运行单元测试...'
                sh 'mvn clean package -DskipTests=false'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                    jacoco(execPattern: '**/target/jacoco.exec')
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                echo '构建 Docker 镜像...'
                script {
                    def dockerImage = docker.build("${DOCKER_REGISTRY}/${APP_NAME}:${env.BUILD_NUMBER}")
                    dockerImage.tag("${DOCKER_REGISTRY}/${APP_NAME}:latest")
                }
            }
        }
        
        stage('Docker Push') {
            steps {
                echo '推送 Docker 镜像到仓库...'
                script {
                    docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-registry-creds") {
                        docker.image("${DOCKER_REGISTRY}/${APP_NAME}:${env.BUILD_NUMBER}").push()
                        docker.image("${DOCKER_REGISTRY}/${APP_NAME}:latest").push()
                    }
                }
            }
        }
        
        stage('Deploy to Dev') {
            when {
                expression { params.DEPLOY_TO_DEV }
            }
            steps {
                echo '部署到开发环境...'
                script {
                    sh 'docker-compose -f docker-compose.yml up -d'
                }
            }
        }
        
        stage('Approval for Prod') {
            when {
                expression { params.DEPLOY_TO_PROD }
            }
            steps {
                timeout(time: 24, unit: 'HOURS') {
                    input message: '批准部署到生产环境？', ok: '部署'
                }
            }
        }
        
        stage('Deploy to Prod') {
            when {
                expression { params.DEPLOY_TO_PROD }
            }
            steps {
                echo '部署到生产环境...'
            }
        }
    }
    
    post {
        success {
            echo '流水线执行成功！'
            slackSend channel: '#devops-notifications', color: 'good', message: "构建成功: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${env.BUILD_URL}"
        }
        failure {
            echo '流水线执行失败！'
            slackSend channel: '#devops-notifications', color: 'danger', message: "构建失败: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${env.BUILD_URL}"
        }
        unstable {
            echo '流水线执行不稳定！'
            slackSend channel: '#devops-notifications', color: 'warning', message: "构建不稳定: ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${env.BUILD_URL}"
        }
        always {
            echo '清理工作区...'
            cleanWs()
        }
    }
}
