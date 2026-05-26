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
        KUBECONFIG = credentials('kubeconfig')
        HELM_NAMESPACE_DEV = 'dev'
        HELM_NAMESPACE_PROD = 'prod'
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
        booleanParam(name: 'DEPLOY_TO_PROD', defaultValue: false, description: '是否部署到生产环境 (需要人工确认)')
        booleanParam(name: 'ENABLE_CANARY', defaultValue: false, description: '是否启用灰度发布')
        choice(name: 'CANARY_WEIGHT', choices: ['10', '20', '30', '50', '100'], description: '灰度版本流量权重百分比')
        string(name: 'CANARY_VERSION', defaultValue: '1.1.0', description: '灰度发布版本号 (新版本)')
        string(name: 'STABLE_VERSION', defaultValue: '1.0.0', description: '稳定版版本号 (当前线上运行版本)')
        booleanParam(name: 'FIRST_DEPLOY', defaultValue: false, description: '是否是首次部署 (会部署稳定版)')
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
                sh 'helm version --client'
                sh 'kubectl version --client'
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
                    docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-registry-creds') {
                        docker.image("${DOCKER_REGISTRY}/${APP_NAME}:${env.BUILD_NUMBER}").push()
                        docker.image("${DOCKER_REGISTRY}/${APP_NAME}:latest").push()
                    }
                }
            }
        }
        
        stage('Lint Helm Chart') {
            steps {
                echo '检查 Helm Chart 语法...'
                sh 'helm lint charts/jvm-demo'
            }
        }
        
        stage('Deploy to Dev') {
            when {
                expression { params.DEPLOY_TO_DEV }
            }
            steps {
                echo '部署到开发环境...'
                script {
                    sh """
                        helm upgrade --install ${APP_NAME} charts/jvm-demo \\
                            --namespace ${HELM_NAMESPACE_DEV} \\
                            --create-namespace \\
                            --values charts/jvm-demo/values-dev.yaml \\
                            --set image.tag=${env.BUILD_NUMBER} \\
                            --set image.repository=${DOCKER_REGISTRY}/${APP_NAME} \\
                            --wait \\
                            --timeout 5m
                    """
                    echo '开发环境部署成功！'
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
        
        stage('Deploy to Prod with Canary') {
            when {
                expression { params.DEPLOY_TO_PROD }
            }
            steps {
                script {
                    if (params.ENABLE_CANARY) {
                        echo "=== 灰度发布流程 ==="
                        echo "稳定版 (线上运行): ${params.STABLE_VERSION}"
                        echo "灰度版 (新版本): ${params.CANARY_VERSION}"
                        echo "初始灰度流量: ${params.CANARY_WEIGHT}%"
                        
                        if (params.FIRST_DEPLOY) {
                            echo "首次部署：同时部署稳定版和灰度版"
                            sh """
                                helm upgrade --install ${APP_NAME} charts/jvm-demo \\
                                    --namespace ${HELM_NAMESPACE_PROD} \\
                                    --create-namespace \\
                                    --values charts/jvm-demo/values-prod.yaml \\
                                    --set image.repository=${DOCKER_REGISTRY}/${APP_NAME} \\
                                    --set stable.version=${params.STABLE_VERSION} \\
                                    --set canary.enabled=true \\
                                    --set canary.weight=${params.CANARY_WEIGHT} \\
                                    --set canary.version=${params.CANARY_VERSION} \\
                                    --wait \\
                                    --timeout 5m
                            """
                        } else {
                            echo "非首次部署：仅部署灰度版，稳定版保持不变"
                            sh """
                                helm upgrade --install ${APP_NAME} charts/jvm-demo \\
                                    --namespace ${HELM_NAMESPACE_PROD} \\
                                    --values charts/jvm-demo/values-prod.yaml \\
                                    --set image.repository=${DOCKER_REGISTRY}/${APP_NAME} \\
                                    --set stable.version=${params.STABLE_VERSION} \\
                                    --set canary.enabled=true \\
                                    --set canary.weight=${params.CANARY_WEIGHT} \\
                                    --set canary.version=${params.CANARY_VERSION} \\
                                    --reuse-values \\
                                    --wait \\
                                    --timeout 5m
                            """
                        }
                    } else {
                        echo "=== 全量发布流程 ==="
                        echo "将稳定版升级到: ${params.CANARY_VERSION}"
                        
                        if (params.FIRST_DEPLOY) {
                            echo "首次部署：部署稳定版"
                            sh """
                                helm upgrade --install ${APP_NAME} charts/jvm-demo \\
                                    --namespace ${HELM_NAMESPACE_PROD} \\
                                    --create-namespace \\
                                    --values charts/jvm-demo/values-prod.yaml \\
                                    --set image.repository=${DOCKER_REGISTRY}/${APP_NAME} \\
                                    --set stable.version=${params.CANARY_VERSION} \\
                                    --set canary.enabled=false \\
                                    --wait \\
                                    --timeout 5m
                            """
                        } else {
                            echo "全量升级：升级稳定版到新版本"
                            sh """
                                helm upgrade --install ${APP_NAME} charts/jvm-demo \\
                                    --namespace ${HELM_NAMESPACE_PROD} \\
                                    --values charts/jvm-demo/values-prod.yaml \\
                                    --set image.repository=${DOCKER_REGISTRY}/${APP_NAME} \\
                                    --set stable.version=${params.CANARY_VERSION} \\
                                    --set canary.enabled=false \\
                                    --reuse-values \\
                                    --wait \\
                                    --timeout 5m
                            """
                        }
                    }
                    echo '生产环境部署成功！'
                }
            }
        }
        
        stage('Verify Deployment') {
            when {
                expression { params.DEPLOY_TO_PROD || params.DEPLOY_TO_DEV }
            }
            steps {
                echo '验证部署...'
                script {
                    def namespace = params.DEPLOY_TO_PROD ? HELM_NAMESPACE_PROD : HELM_NAMESPACE_DEV
                    sh """
                        kubectl rollout status deployment/${APP_NAME} -n ${namespace} --timeout=2m
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=${APP_NAME}
                    """
                    if (params.ENABLE_CANARY && params.DEPLOY_TO_PROD) {
                        sh "kubectl get pods -n ${HELM_NAMESPACE_PROD} -l role=canary"
                    }
                    echo '部署验证完成！'
                }
            }
        }
        
        stage('Run Integration Tests (Dev)') {
            when {
                expression { params.DEPLOY_TO_DEV }
            }
            steps {
                echo '运行集成测试...'
                script {
                    // 这里可以添加实际的集成测试
                    echo '集成测试示例 - 请根据实际需求配置'
                }
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
