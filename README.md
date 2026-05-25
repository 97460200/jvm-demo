# JVM Demo - CPU & 内存溢出模拟

一个用于模拟 CPU 飙高和内存溢出场景的 Spring Boot 微服务演示项目。

## 功能特性

- 🚀 **CPU 飙高模拟** - 通过多线程密集计算模拟高 CPU 负载
- 💾 **内存溢出模拟** - 持续分配内存触发 OOM（Out Of Memory）
- 📊 **实时监控** - 显示活跃线程数和内存使用情况
- 🌐 **Web 界面** - 简洁美观的操作页面

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Maven
- Thymeleaf

## 快速开始

### 1. 构建项目

```bash
mvn clean package
```

### 2. 运行应用

```bash
mvn spring-boot:run
```

或者直接运行 JAR 包：

```bash
java -jar target/jvm-demo-1.0.0.jar
```

### 3. 访问应用

打开浏览器访问：http://localhost:8080

## 使用说明

### CPU 飙高模拟

1. 在页面上选择线程数（2/4/8/16）
2. 点击「启动 CPU 飙高」
3. 观察系统 CPU 使用率飙升
4. 点击「停止」结束模拟

### 内存溢出模拟

1. 点击「触发内存溢出」
2. 观察内存使用持续增长
3. 最终会抛出 OOM 异常
4. 点击「清理内存」释放已分配内存

## 注意事项

⚠️ **警告**：
- 这些操作会占用大量系统资源，请谨慎使用
- 内存溢出会导致应用崩溃
- 建议在测试环境或虚拟机中使用

## CI 流程

项目配置了 GitHub Actions CI 流程，在每次 push 或 pull request 时自动：
- 检出代码
- 设置 JDK 17
- 使用 Maven 构建
- 运行测试

## 项目结构

```
jvm-demo/
├── src/
│   ├── main/
│   │   ├── java/com/example/jvmdemo/
│   │   │   ├── JvmDemoApplication.java
│   │   │   ├── controller/
│   │   │   │   └── SimulationController.java
│   │   │   └── service/
│   │   │       └── SimulationService.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── templates/
│   │           └── index.html
├── pom.xml
└── .github/workflows/
    └── ci.yml
```

## 许可证

MIT License
