# Helm 部署和灰度发布指南

本文档详细介绍如何使用 Helm 部署 JVM Demo 应用以及如何实现灰度发布。

## 前提条件

- Kubernetes 1.19+
- Helm 3.0+
- Nginx Ingress Controller (用于灰度发布的流量控制)
- Docker 镜像仓库

## 目录结构

```
charts/
└── jvm-demo/
    ├── Chart.yaml          # Chart 元数据
    ├── values.yaml         # 默认配置值
    ├── values-dev.yaml     # 开发环境配置
    ├── values-prod.yaml    # 生产环境配置
    └── templates/          # Kubernetes 模板
        ├── deployment.yaml
        ├── service.yaml
        ├── ingress.yaml
        ├── serviceaccount.yaml
        ├── servicemonitor.yaml
        ├── canary-deployment.yaml
        ├── canary-ingress.yaml
        └── _helpers.tpl
```

## 基础部署

### 1. 添加仓库并更新

```bash
# 如果是本地开发
cd /path/to/jvm-demo

# 验证 Chart 语法
helm lint charts/jvm-demo

# 渲染模板查看
helm template jvm-demo charts/jvm-demo --namespace dev
```

### 2. 部署到开发环境

```bash
# 首次部署
helm upgrade --install jvm-demo charts/jvm-demo \
  --namespace dev \
  --create-namespace \
  --values charts/jvm-demo/values-dev.yaml \
  --set image.tag=your-tag \
  --set image.repository=registry.example.com/jvm-demo
```

### 3. 部署到生产环境

```bash
helm upgrade --install jvm-demo charts/jvm-demo \
  --namespace prod \
  --create-namespace \
  --values charts/jvm-demo/values-prod.yaml \
  --set image.tag=prod-1.0.0 \
  --set image.repository=registry.example.com/jvm-demo
```

### 4. 查看部署状态

```bash
# 查看 Helm release
helm list -n prod

# 查看 Kubernetes 资源
kubectl get all -n prod

# 查看 Pod 详细信息
kubectl get pods -n prod -o wide

# 查看应用日志
kubectl logs -f deployment/jvm-demo -n prod
```

## 灰度发布 (Canary Release)

灰度发布使用 Nginx Ingress 的 canary 注解实现流量分发。

### 准备工作

确保 Nginx Ingress Controller 已安装：

```bash
kubectl get pods -n ingress-nginx
```

### 1. 部署稳定版本 (v1.0.0)

```bash
# 首先部署稳定版本
helm upgrade --install jvm-demo charts/jvm-demo \
  --namespace prod \
  --create-namespace \
  --values charts/jvm-demo/values-prod.yaml \
  --set image.tag=prod-1.0.0 \
  --set image.repository=registry.example.com/jvm-demo

# 验证部署
kubectl get pods -n prod
kubectl get ingress -n prod
```

### 2. 启用灰度发布 (10% 流量到 v1.1.0)

```bash
helm upgrade --install jvm-demo charts/jvm-demo \
  --namespace prod \
  --values charts/jvm-demo/values-prod.yaml \
  --set image.tag=prod-1.0.0 \
  --set image.repository=registry.example.com/jvm-demo \
  --set canary.enabled=true \
  --set canary.weight=10 \
  --set canary.version=prod-1.1.0
```

此时会有两个 deployment 和两个 service：
- `jvm-demo` - 稳定版本 (90% 流量)
- `jvm-demo-canary` - 灰度版本 (10% 流量)

### 3. 逐步增加灰度流量

```bash
# 增加到 30% 流量
helm upgrade jvm-demo charts/jvm-demo \
  --namespace prod \
  --values charts/jvm-demo/values-prod.yaml \
  --set image.tag=prod-1.0.0 \
  --set image.repository=registry.example.com/jvm-demo \
  --set canary.enabled=true \
  --set canary.weight=30 \
  --set canary.version=prod-1.1.0

# 增加到 50% 流量
helm upgrade jvm-demo charts/jvm-demo \
  --namespace prod \
  --values charts/jvm-demo/values-prod.yaml \
  --set image.tag=prod-1.0.0 \
  --set image.repository=registry.example.com/jvm-demo \
  --set canary.enabled=true \
  --set canary.weight=50 \
  --set canary.version=prod-1.1.0
```

### 4. 验证灰度流量

可以通过多次访问应用来观察：

```bash
# 查看当前流量分发情况
kubectl describe ingress jvm-demo -n prod
kubectl describe ingress jvm-demo-canary -n prod

# 测试访问（多次运行观察响应）
for i in {1..10}; do
  curl -s http://jvm-demo.example.com/actuator/health
  echo ""
  sleep 1
done
```

### 5. 监控和验证

```bash
# 查看两个版本的 Pod 运行状态
kubectl get pods -n prod -L app.kubernetes.io/version

# 查看灰度版本日志
kubectl logs -f -l role=canary -n prod

# 查看 Ingress 详细信息
kubectl get ingress -n prod -o yaml
```

### 6. 全量发布新版本

确认新版本稳定后，进行全量发布：

```bash
# 将主版本升级到 1.1.0，同时禁用 canary
helm upgrade jvm-demo charts/jvm-demo \
  --namespace prod \
  --values charts/jvm-demo/values-prod.yaml \
  --set image.tag=prod-1.1.0 \
  --set image.repository=registry.example.com/jvm-demo \
  --set canary.enabled=false
```

### 7. 回滚灰度版本

如果灰度版本出现问题，可以回滚：

```bash
# 禁用 canary，保留稳定版本
helm upgrade jvm-demo charts/jvm-demo \
  --namespace prod \
  --values charts/jvm-demo/values-prod.yaml \
  --set image.tag=prod-1.0.0 \
  --set image.repository=registry.example.com/jvm-demo \
  --set canary.enabled=false

# 或者回滚到上一个版本
helm rollback jvm-demo -n prod
```

## 高级灰度发布策略

### 基于 Header 的灰度发布

修改 `templates/canary-ingress.yaml` 添加基于 header 的规则：

```yaml
annotations:
  nginx.ingress.kubernetes.io/canary: "true"
  nginx.ingress.kubernetes.io/canary-by-header: "X-Canary"
  nginx.ingress.kubernetes.io/canary-by-header-value: "always"
```

测试：
```bash
# 指定 header 访问灰度版本
curl -H "X-Canary: always" http://jvm-demo.example.com
```

### 基于 Cookie 的灰度发布

```yaml
annotations:
  nginx.ingress.kubernetes.io/canary: "true"
  nginx.ingress.kubernetes.io/canary-by-cookie: "canary_cookie"
```

## 其他 Helm 命令

### 检查发布历史

```bash
helm history jvm-demo -n prod
```

### 回滚到特定版本

```bash
helm rollback jvm-demo 2 -n prod  # 回滚到第2个版本
```

### 卸载应用

```bash
helm uninstall jvm-demo -n prod
```

### 暂停和恢复发布

```bash
# 查看当前状态
helm status jvm-demo -n prod

# 如果需要，可以使用 --dry-run 预览变更
helm upgrade jvm-demo charts/jvm-demo --namespace prod --dry-run --debug
```

## 故障排查

### 灰度发布不生效

1. 检查 Nginx Ingress Controller 是否正确安装
2. 验证 ingress 资源是否正确创建
3. 检查 ingress 注解是否正确
4. 查看 Nginx Ingress Controller 日志

```bash
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx
```

### Pod 无法启动

```bash
# 查看 Pod 状态
kubectl get pods -n prod
kubectl describe pod <pod-name> -n prod

# 查看事件
kubectl get events -n prod --sort-by='.lastTimestamp'
```

### 健康检查失败

检查应用日志和探针配置：

```bash
kubectl describe deployment jvm-demo -n prod
```

## 与 Jenkins 集成

参考 `Jenkinsfile` 中的配置，确保：

1. Jenkins Agent 已安装 Helm 和 kubectl
2. 配置了 kubeconfig 凭据
3. 配置了 Docker Registry 凭据
4. 配置了正确的 Namespace

## 最佳实践

1. **灰度比例**：从 10% 开始，观察指标后逐步增加
2. **监控指标**：关注错误率、延迟、资源使用率
3. **自动化**：结合监控告警自动调整灰度流量或回滚
4. **快速回滚**：准备好快速回滚方案
5. **数据隔离**：确保灰度版本不会影响生产数据
