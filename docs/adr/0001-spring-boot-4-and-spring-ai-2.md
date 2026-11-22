# ADR 0001：采用 Spring Boot 4.1.x 与 Spring AI 2.0.x，1.0 暂不引入 Spring AI Alibaba

- **状态**：已被 ADR 0004 取代
- **日期**：2026-11-22
- **决策者**：项目所有者

> **2026-11-22 更新**：项目决定按设计文档保持 Spring Boot 3.5.x + Spring AI Alibaba，本 ADR 的 Boot 4 方案不再采用。当前有效决策见 `docs/adr/0004-keep-spring-boot-3-5-and-saa.md`。

## 背景

1.0 基线原先写的是 JDK 21 + Spring Boot 3.x + Spring AI Alibaba。2026-11 的实际情况是：

- Spring Boot 3.3/3.4 已 EOL，3.5.16 是最后一个 OSS 版本，3.5 线已于 2026-06-30 EOL。
- Spring AI 2.0.x 对应 Spring Boot 4.0/4.1；Spring AI 2.1.x 对应 Spring Boot 4.2。
- Spring AI Alibaba 1.1.x 基于 Spring AI 1.1.x + Boot 3.5.x，不能运行在 Boot 4。
- Spring AI Alibaba 的 Boot 4 适配版本是 `2.0.0-M1.1` milestone，截至决策时没有 GA。

1.0 的 Provider 只需要 DeepSeek 和 Kimi，两者都可以走 OpenAI 兼容协议，不依赖 SAA 的多厂商 connector。

## 决策

1. 采用 **Spring Boot 4.1.3**、**Spring AI 2.0.3**、**springdoc-openapi 3.2.0**。
2. 1.0 不引入 Spring AI Alibaba；Provider 走 Spring AI 的 OpenAI 兼容 connector。
3. Spring AI Alibaba 作为扩展路径保留：等其 Boot 4 兼容版本 GA 后，再评估接入 DashScope/Qwen。
4. 同步更新 `ReleaseBaseline`、`TechnicalSolution`、`DemandAnalysis`、`AiProgrammingGuide`、`CLAUDE.md` 和 Spec-Kit 计划中的版本口径。

## 影响

- Spring Boot 4 带来 Jackson 3（`tools.jackson`）、Jakarta EE 11、Hibernate 7、starter 模块化等迁移点。
- Spring AI 2.x 的 tool-calling 进入 advisor 链；必须确认调用 `ChatModel` 时不会触发自动 Tool 执行，ReAct 调度仍由 `ReActLoop` + `ToolExecutor` 唯一控制。
- MCP SDK 与 Jackson 3 的兼容性需要在 US-4 前验证。
- 本文是技术栈口径变更的唯一记录；旧文档中“Spring Boot 3.x + Spring AI Alibaba”的表述如未同步，以本文和 `docs/engineering/00-versions.md` 为准。

## 备选方案

| 方案 | 结果 | 未采用原因 |
|---|---|---|
| Boot 3.5.16 + Spring AI 1.1.x + SAA 1.1.2.x | 兼容 | OSS 已 EOL，不符合“支持 4.x”的决策 |
| Boot 4.x + SAA 2.0.0-M1.1 | 可用但无 GA | milestone、维护停滞，不适合作为 1.0 基线 |
| Boot 4.x + Spring AI 2.0.x | 当前线、稳定 | 采用 |
