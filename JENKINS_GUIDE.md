# Jenkins CI/CD 流水线部署指南

本文档详细说明了如何在企业环境中部署和运行本项目的 Jenkins CI/CD 流水线。

## 1. 环境准备

### 1.1 必需组件

- Jenkins 2.346+
- JDK 17+
- Maven 3.8+
- Docker 20.10+
- Git 2.30+
- SonarQube 9.x (可选但推荐)
- Kubernetes 集群 (可选，用于生产部署)

### 1.2 必需的 Jenkins 插件

- Pipeline (工作流聚合插件)
- Git Plugin
- Docker Plugin
- Docker Pipeline
- SonarQube Scanner
- JaCoCo
- JUnit
- Slack Notification Plugin
- Kubernetes Plugin (可选)
- Blue Ocean (推荐)

### 1.3 安装推荐插件的方式

在 Jenkins 管理界面：
1. 进入 Manage Jenkins → Plugins
2. 搜索并安装上述插件
3. 重启 Jenkins

## 2. 配置 Jenkins 凭据

在 Jenkins 中配置以下凭据（建议使用 "Secret text" 或 "Username and password" 类型）：

| 凭据ID | 类型 | 描述 |
|--------|------|------|
| `docker-registry-url` | Secret text | Docker 镜像仓库地址 (如: `registry.example.com`) |
| `docker-username` | Secret text | Docker 仓库用户名 |
| `docker-password` | Secret text | Docker 仓库密码或访问令牌 |
| `docker-registry-creds` | Username and password | Docker 仓库完整凭据 |
| `sonar-token` | Secret text | SonarQube 访问令牌 |
| `git-credentials` | Username and password | Git 仓库访问凭据 |
| `slack-token` | Secret text | Slack 通知机器人令牌 |

## 3. 配置 Jenkins 系统

### 3.1 配置 SonarQube

1. Manage Jenkins → System
2. 找到 SonarQube servers
3. 添加 SonarQube 服务器
   - Name: `SonarQube`
   - Server URL: `https://your-sonarqube-server.com`
   - Server authentication token: 选择 `sonar-token` 凭据

### 3.2 配置全局工具

1. Manage Jenkins → Global Tool Configuration
2. JDK:
   - Name: `JDK 17`
   - JAVA_HOME: 指向 JDK 17 安装路径
3. Maven:
   - Name: `Maven 3.9`
   - MAVEN_HOME: 指向 Maven 安装路径
4. Docker: (可选)
   - 配置 Docker 安装路径

### 3.3 配置 Slack (可选)

1. Manage Jenkins → System
2. 找到 Slack
3. 配置 Slack workspace 和凭据

## 4. 创建 Jenkins Pipeline 任务

### 4.1 创建新任务

1. 点击 Jenkins 首页 "New Item"
2. 输入任务名称: `jvm-demo-pipeline`
3. 选择 "Pipeline"
4. 点击 "OK"

### 4.2 配置 Pipeline

1. General 选项卡:
   - 勾选 "GitHub project"，输入项目 URL
   - 勾选 "This project is parameterized"
   - 添加 Boolean 参数:
     - `RUN_SONAR`: 默认 true
     - `DEPLOY_TO_DEV`: 默认 true
     - `DEPLOY_TO_PROD`: 默认 false

2. Build Triggers 选项卡:
   - 勾选 "GitHub hook trigger for GITScm polling"
   - 或者配置定时构建: `H/15 * * * *` (每15分钟检查一次)

3. Pipeline 选项卡:
   - Definition: 选择 "Pipeline script from SCM"
   - SCM: 选择 "Git"
   - Repository URL: `https://github.com/97460200/jvm-demo.git`
   - Credentials: 选择 git-credentials
   - Branch Specifier: `*/main`
   - Script Path: `Jenkinsfile`

4. 点击 "Save"

## 5. 流水线阶段说明

我们的企业级 CI/CD 流水线包含以下阶段：

### 5.1 Checkout Code (代码检出)
- 从 Git 仓库拉取最新代码

### 5.2 Initialize (环境初始化)
- 验证环境工具版本

### 5.3 Static Code Analysis (静态代码分析)
- SonarQube 代码质量扫描
- 质量门禁检查

### 5.4 Build & Test (构建与测试)
- Maven 编译
- 单元测试执行
- JaCoCo 代码覆盖率检查

### 5.5 Docker Build (构建镜像)
- 使用 Dockerfile 构建镜像
- 打上版本号和 latest 标签

### 5.6 Docker Push (推送镜像)
- 推送镜像到私有仓库

### 5.7 Deploy to Dev (部署到开发环境)
- 使用 Docker Compose 部署到开发环境

### 5.8 Approval for Prod (生产环境审批)
- 人工审批环节（24小时超时）

### 5.9 Deploy to Prod (部署到生产环境)
- 部署到 Kubernetes 集群

## 6. 配置 GitHub Webhook (自动触发)

1. 在 GitHub 仓库页面，进入 Settings → Webhooks
2. 点击 "Add webhook"
3. Payload URL: `http://your-jenkins-url/github-webhook/`
4. Content type: `application/json`
5. 选择触发事件（建议 "Just the push event"）
6. 点击 "Add webhook"

## 7. Kubernetes 部署 (可选)

### 7.1 使用 kubectl 部署

```bash
# 应用部署配置
envsubst < k8s/deployment.yaml | kubectl apply -f -

# 查看部署状态
kubectl get pods -l app=jvm-demo
kubectl get svc jvm-demo-service
```

### 7.2 健康检查配置

项目已配置 Spring Boot Actuator 的健康检查端点：
- 就绪探针: `/actuator/health/readiness`
- 存活探针: `/actuator/health/liveness`

## 8. 常见问题排查

### 8.1 权限问题

确保 Jenkins 用户有 Docker 访问权限：
```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

### 8.2 SonarQube 扫描失败

检查 SonarQube 服务是否正常，并验证质量门禁配置。

### 8.3 Docker 镜像拉取失败

检查 Docker 仓库凭据配置和网络连接。

## 9. 流水线最佳实践

1. **安全第一**: 所有凭据通过 Jenkins 凭据管理，不要硬编码
2. **质量门禁**: 代码质量不达标不允许合并和部署
3. **测试覆盖**: 保持单元测试覆盖率在 70% 以上
4. **快速反馈**: 流水线尽量在 10-15 分钟内完成
5. **回滚机制**: 部署失败时快速回滚到上一个稳定版本
6. **监控告警**: 流水线状态变更及时通知团队

## 10. 参考资料

- [Jenkins 官方文档](https://www.jenkins.io/doc/)
- [Spring Boot Docker 部署](https://spring.io/guides/topicals/spring-boot-docker/)
- [Kubernetes 部署指南](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
- [SonarQube 质量门禁](https://docs.sonarqube.org/latest/user-guide/quality-gates/)
