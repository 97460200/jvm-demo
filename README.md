# JVM 故障演练平台

一个用于模拟和学习 JVM 常见故障场景的 Spring Boot 微服务演示项目。

## ✨ 功能特性

### 故障模拟
- 🚀 **CPU 飙高** - 通过多线程密集计算模拟 CPU 100% 负载
- 💾 **内存溢出** - 持续分配大对象触发 OutOfMemoryError
- 🔒 **线程死锁** - 创建互相等待的线程，模拟典型的死锁场景

### 监控与诊断
- 📊 **实时图表** - 使用 Chart.js 展示内存和线程数趋势
- 📄 **线程 Dump 导出** - 一键导出完整线程堆栈用于分析
- 🔴 **告警提示** - 异常状态时指标卡片高亮闪烁
- 💻 **丰富指标** - CPU 核心数、活跃线程、内存使用等全面监控

### DevOps
- 🐳 **Docker 支持** - 提供 Dockerfile 和 docker-compose 快速部署
- 🔄 **GitHub Actions CI** - 自动构建和测试流程

## 🛠️ 技术栈

- **Java 17** - 最新 LTS 版本
- **Spring Boot 3.2.0** - 企业级应用框架
- **Maven** - 项目构建工具
- **Thymeleaf** - 服务端模板引擎
- **Chart.js** - 前端图表库
- **Docker** - 容器化部署

## 🚀 快速开始

### 方式一：Maven 运行

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd jvm-demo

# 2. 构建并运行
mvn spring-boot:run
```

### 方式二：JAR 包运行

```bash
mvn clean package
java -jar target/jvm-demo-1.0.0.jar
```

### 方式三：Docker 运行

```bash
# 构建并启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止
docker-compose down
```

### 访问应用

打开浏览器访问：http://localhost:8080

## 📖 使用指南

### CPU 飙高演练

1. 在页面上选择线程数（2/4/8/16）
2. 点击「启动」按钮
3. 观察实时图表和系统资源监控
4. 点击「停止」结束模拟

### 内存溢出演练

1. 点击「触发 OOM」
2. 观察内存图表持续增长
3. 应用最终会因内存不足崩溃
4. 重启应用或点击「清理」释放内存

### 死锁演练

1. 点击「制造死锁」
2. 查看死锁线程数指标变化
3. 点击「导出线程 Dump」下载分析
4. 使用 VisualVM 或其他工具分析堆栈

### 诊断工具使用

- **线程 Dump 导出** - 获取完整的线程状态快照
- **实时监控** - 观察各项指标的动态变化
- **趋势图表** - 分析内存和线程的历史走势

## 📁 项目结构

```
jvm-demo/
├── src/
│   ├── main/
│   │   ├── java/com/example/jvmdemo/
│   │   │   ├── JvmDemoApplication.java
│   │   │   ├── controller/
│   │   │   │   └── SimulationController.java    # API 控制器
│   │   │   └── service/
│   │   │       └── SimulationService.java        # 核心模拟逻辑
│   │   └── resources/
│   │       ├── application.properties
│   │       └── templates/
│   │           └── index.html                    # Web 界面
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
└── .github/workflows/
    └── ci.yml                                    # GitHub Actions 配置
```

## 🚨 注意事项

⚠️ **重要警告**：
- 这些操作会占用大量系统资源，请谨慎使用
- 内存溢出会导致应用崩溃，需要重启
- 建议在测试环境或虚拟机中使用
- **切勿在生产环境运行此应用！**

## 🔄 CI 流程

项目配置了 GitHub Actions CI 流程，在每次 push 或 pull request 时自动：
- 检出代码
- 设置 JDK 17
- 使用 Maven 构建
- 运行测试

## 💡 学习资源

此项目可用于学习：
- JVM 故障排查技巧
- 性能调优方法
- 线程 Dump 分析
- 内存泄漏检测
- Docker 容器化部署

## 📄 许可证

MIT License
