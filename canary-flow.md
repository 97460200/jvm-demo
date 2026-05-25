# 自动化灰度发布流程图

```mermaid
graph TD
    A[代码提交] --> B[Jenkins触发流水线]
    
    subgraph Jenkins流水线
        B --> C[代码拉取与编译]
        C --> D[静态代码分析与测试]
        D --> E[构建Docker镜像]
        E --> F[推送镜像到仓库]
        F --> G[部署到开发环境]
        G --> H[人工审批生产发布]
    end
    
    H --> I{是否启用灰度?}
    
    I -->|否| J[全量部署到生产]
    I -->|是| K[部署稳定版+灰度版]
    
    K --> L[初始流量分配10%]
    
    subgraph 自动灰度控制器
        L --> M[启动Canary Controller]
        M --> N[Prometheus监控指标]
        N --> O{健康检查}
        O -->|正常| P[增加流量权重+10%]
        O -->|异常| Q[降低流量权重-10%]
        Q --> R{连续错误>=3次?}
        R -->|是| S[自动回滚]
        R -->|否| T[继续监控]
        P --> U{权重达到100%?}
        U -->|否| T
        U -->|是| V[全量发布完成]
        T --> N
    end
    
    J --> W[发布完成]
    S --> W
    V --> W
```

## 详细流程说明

### 1. 开发流程
1. 开发人员提交代码到Git仓库
2. Jenkins自动触发CI/CD流水线
3. 执行代码检查、单元测试、构建镜像
4. 自动部署到开发环境验证

### 2. 生产发布
- **全量发布**：直接部署新版本，100%流量到新版本
- **灰度发布**：
  - 同时部署稳定版和灰度版
  - 初始分配10%流量到灰度版
  - 启动自动灰度控制器

### 3. 自动灰度调整
自动灰度控制器持续运行，每隔30秒执行一次：
1. 从Prometheus查询指标（错误率、延迟、内存使用等）
2. 检查应用健康状态
3. 健康则增加灰度权重（+10%）
4. 不健康则降低灰度权重（-10%）
5. 连续3次错误自动回滚
6. 权重达到100%则完成全量发布

## 关键组件

| 组件 | 文件位置 | 作用 |
|------|---------|------|
| Jenkins流水线 | [Jenkinsfile](file:///workspace/Jenkinsfile) | 代码构建、测试、部署 |
| 灰度控制器脚本 | [scripts/canary_controller.py](file:///workspace/scripts/canary_controller.py) | 监控指标、自动调整权重 |
| Helm Chart | [charts/jvm-demo/](file:///workspace/charts/jvm-demo/) | Kubernetes资源管理 |
| Nginx Ingress | [charts/jvm-demo/templates/canary-ingress.yaml](file:///workspace/charts/jvm-demo/templates/canary-ingress.yaml) | 流量分配控制 |
