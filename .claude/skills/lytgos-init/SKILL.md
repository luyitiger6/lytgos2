---
name: lytgos-init
description: >-
  初始化或重建 LytgOS2 的工程地基：Spring Boot 3.5.x + Spring AI Alibaba + JDK 21 Maven 多模块、
  Maven Wrapper、Enforcer、结构化日志、健康检查、统一 API 信封、springdoc、
  Google 格式 + Checkstyle + PMD 7 + SpotBugs/FindSecBugs + ArchUnit + JaCoCo、
  gitleaks/Dependency-Check/SBOM 与 CI/pre-commit。用于“初始化项目/搭骨架/
  加日志监控/加开发规范/加质量门禁/加代码安全检查”。不实现五大核心业务能力。
---

# lytgos-init：一次性工程地基

把“工程地基”装好并验证；业务能力（Provider / ReAct / Memory / Tool / Web）不在本 skill 范围内。

## 权威输入

实施前先读，冲突时以这些为准：

- `docs/engineering/00-versions.md`：版本矩阵
- `docs/engineering/01-build.md`：构建与依赖方向
- `docs/engineering/02-runtime.md`：日志、健康检查、HTTP
- `docs/engineering/03-api.md`：统一信封与 OpenAPI
- `docs/engineering/04-quality.md`：质量门禁与 P3C 策略
- `docs/engineering/05-security.md`：安全检查
- `docs/engineering/06-ci.md`：CI 与本地钩子
- `docs/engineering/07-module-standards.md`：模块矩阵
- `docs/adr/0004-keep-spring-boot-3-5-and-saa.md`
- `docs/adr/0002-google-format-and-p3c.md`
- `docs/adr/0003-no-prometheus-in-1.0.md`

## 硬约束

- JDK 21，Spring Boot 3.5.16，Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.4-security-fix；按设计文档保留 SAA。
- Boot 3.5 OSS 已 EOL；这是兼容 SAA 的设计基线，升级 Boot 4 必须等 SAA 的 Boot 4 兼容版本 GA（ADR 0004）。
- Spring MVC + 虚拟线程；不引入 WebFlux。
- 1.0 不做 Prometheus/Grafana/trace ID 串联；只保留 health/info + 日志 + 审计表。
- 格式唯一权威是 Spotless + google-java-format（GOOGLE）；P3C 是补充报告，不阻断。
- 密钥只用 `${ENV_VAR}`；不写明文。
- 只新增基础设施与配置，不替换业务代码。

## 步骤

### 1. 确认参数与边界

确认 `groupId`、根 `artifactId`、9 个模块、端口（默认 8080）、JDK（21）。
如果用户要求引入 Prometheus、认证、CORS 收紧，或移除 SAA/切换 Boot 4，先指出这与 1.0 基线冲突，要求先改 `ReleaseBaseline`/ADR，不要静默变更。

### 2. 构建骨架

- 根 `pom.xml`：`packaging=pom`，`spring-boot-starter-parent` 3.5.16。
- 九模块：core、provider、memory、tool、channel-cli、web、storage、cli、boot。
- `lytgos-boot` 打 fat JAR，`mainClass=io.lytgos.boot.LytgosApplication`。
- 生成 Maven Wrapper：`mvn -N wrapper:wrapper -Dmaven=3.9.8`。
- 父 POM 锁定：Spring AI 1.1.2 BOM、Spring AI Alibaba 1.1.2.4-security-fix BOM、MCP 2.2.0 BOM、springdoc 2.8.17、Picocli 4.7.6。
- `maven-enforcer-plugin`：JDK `[21,)`、Maven `[3.9.0,)`、显式插件版本、重复依赖检查。

### 3. 运行时地基

- `application.yaml`：虚拟线程、优雅停机、请求上限、`management` 只暴露 health/info、`lytgos.workspace`、`spring.ai.dashscope.enabled=false`。
- `logback-spring.xml`：开发 console；`prod` 用 `StructuredLogEncoder`（ecs）+ 滚动文件。
- 统一 SLF4J；禁止 `System.out`；CLI 输出通过 `PrintWriter` 注入。
- `lytgos-web`：`ApiResponse`、`ErrorCode`、`GlobalExceptionHandler`、`OpenApiConfig`、`SystemApiController`。
- `lytgos-web` 用 `spring-boot-starter-web`；Boot 3.5 的测试用 `spring-boot-starter-test` 即可。
- `lytgos-provider` 放入 SAA starter（版本由 SAA BOM/属性管理），默认禁用 DashScope autoconfigure；启用时设置 `spring.ai.dashscope.enabled=true` 和 `DASHSCOPE_API_KEY`。

### 4. 质量门禁

父 POM 绑定：

- Spotless 3.11.0 + gJF 2.0.0（GOOGLE，`cleanup=false`），`verify` 阶段 `check`。
- Checkstyle 10.21.4，`validate` 阶段 `check`；规则只覆盖格式之外的内容。
- PMD 7.17.0，`verify` 阶段 `check`；规则集 `config/pmd/pmd-ruleset.xml`。
- SpotBugs 4.10.4.0 + FindSecBugs 1.14.0，`verify` 阶段 `check`。
- JaCoCo 0.8.16：先报告，不设阻断阈值。
- ArchUnit 1.5.1：模块无环、core 独立、web 不依赖 adapter、禁止标准流。
- P3C：`p3c` profile，PMD 6.55 + p3c-pmd 2.1.1，`failOnViolation=false`；不能和 PMD 7 同一次调用。

### 5. 安全与 CI

- `.github/workflows/ci.yml`：verify、gitleaks、dependency-review、Dependency-Check（有 `NVD_API_KEY` 时）、SBOM。
- `.githooks/pre-commit`：`spotless:check` + `gitleaks protect --staged`。
- 文档：`docs/engineering/06-ci.md` 写清 `chmod +x .githooks/pre-commit` 与 `git config core.hooksPath .githooks`。

### 6. 验证

```bash
./mvnw clean verify
./mvnw -Pp3c verify            # 报告模式，不应阻断
java -jar lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar
curl -s http://localhost:8080/api/v1/health
curl -s http://localhost:8080/api/v1/info
curl -s http://localhost:8080/v3/api-docs
```

再故意制造一次格式违规，确认 `spotless:check` 报错；制造一次 `System.out`，确认 ArchUnit 报错。

## Definition of Done

- [ ] `./mvnw clean verify` 全绿
- [ ] Boot JAR 可启动，health/info 返回统一信封
- [ ] `/v3/api-docs` 可访问
- [ ] 虚拟线程、优雅停机、请求上限生效
- [ ] 日志在 prod 为 ECS JSON，且无明文密钥
- [ ] Spotless/Checkstyle/PMD/SpotBugs/ArchUnit/JaCoCo 全部接入 `verify`
- [ ] `-Pp3c` 可跑且不阻断
- [ ] CI 与 pre-commit 文档可执行
- [ ] 版本矩阵、ADR、CLAUDE.md 已同步

## 不做什么

- 不实现 Provider/ReAct/Memory/Tool/Web 业务逻辑。
- 不添加 Prometheus、Grafana、认证、RBAC、SSE/WebSocket。
- 不在未修订 `ReleaseBaseline`/ADR 的情况下移除 SAA 或切换 Boot 4。
- 不把 P3C 设为主门禁，不和 PMD 7 混跑。
- 不用 `-DskipTests` 或关闭规则来“让构建变绿”。
