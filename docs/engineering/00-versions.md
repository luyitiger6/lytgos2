# 工程版本基线

> 本文是构建、运行时和质量门禁的版本唯一来源。升级任何版本时，先改这里，再改 POM 与 CI。

## 1. 运行时基线

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | Maven 使用 JDK 21；允许 JDK 21+ 运行 |
| Spring Boot | 3.5.16 | 3.5 线最后一个 OSS 版本；为兼容 SAA 保留 |
| Spring AI | 1.1.2 | SAA 1.1.2.x 的 autoconfigure 依赖 Spring AI 1.1.2 |
| Spring AI Alibaba | 1.1.2.4-security-fix | 1.1.x 线；starter 在 classpath，默认 `spring.ai.dashscope.enabled=false` |
| springdoc-openapi | 2.8.17 | Boot 3.5.x 线 |
| MCP Java SDK | 2.2.0 | 由 `mcp-bom` 管理；US-4 引入 |
| Picocli | 4.7.6 | CLI 命令树 |
| SQLite JDBC / Flyway / JPA | US-1/US-4 再锁定 | 目前骨架未引入 |

## 2. 构建与质量门禁

| 组件 | 版本 | 用途 |
|---|---|---|
| Maven Wrapper | 3.9.8 | 仓库自带 `mvnw` / `mvnw.cmd` |
| Spotless | 3.11.0 | 格式门禁 |
| google-java-format | 2.0.0（GOOGLE 风格） | 格式唯一权威 |
| Checkstyle | 10.21.4 | 命名、import、行宽等规则 |
| PMD | 7.17.0 | 缺陷、复杂度、性能、安全规则 |
| SpotBugs | 4.10.4.0 | 字节码缺陷 |
| FindSecBugs | 1.14.0 | SpotBugs 安全插件 |
| JaCoCo | 0.8.16 | 覆盖率报告 |
| OWASP Dependency-Check | 13.0.0 | SCA；只在 CI/security profile 跑 |
| CycloneDX | 2.9.3 | SBOM |
| ArchUnit | 1.5.1 | 模块依赖与架构规则 |

## 3. 兼容性约束

- Spring AI 1.1.x 对应 Spring Boot 3.5.x；Spring AI 2.x 对应 Boot 4.x，不能混用。
- Spring AI Alibaba 1.1.2.4-security-fix 的 autoconfigure 依赖 Spring AI 1.1.2；不要单独升级 Spring AI 到 1.1.8。
- 已验证的依赖链：SAA 1.1.2.4-security-fix → Spring AI 1.1.2 → Spring Boot 3.5.16。
- SAA starter 默认关闭 DashScope autoconfigure，避免缺少 key 时启动失败；需要 DashScope/Qwen 时再开启并注入 `DASHSCOPE_API_KEY`。
- Spring Boot 3.5 OSS 已于 2026-06-30 EOL。当前选择是为兼容 SAA 的设计基线；升级到 Boot 4 需要等 SAA 的 Boot 4 兼容版本 GA。
- Spring Boot 3.5 使用 Jackson 2；不要引入依赖 Jackson 3 的库。
- P3C 2.1.1 基于 PMD 6.15；不能和 PMD 7 在同一次 Maven 调用中运行。

## 4. 升级规则

1. 先在分支上改本文和 POM。
2. 跑 `./mvnw clean verify`。
3. 跑 `./mvnw -Psecurity verify`（有 `NVD_API_KEY` 时）。
4. 跑一次真实启动，确认 SAA/Spring AI 的 autoconfigure 没有因版本变化失效。
5. 记录 ADR 或更新已有 ADR。
6. 合并后观察一个迭代，再删旧版本说明。
