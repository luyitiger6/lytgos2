# ADR 0004：保持 Spring Boot 3.5.x + Spring AI Alibaba，不中途切换 Boot 4

- **状态**：已接受
- **日期**：2026-11-22
- **决策者**：项目所有者

## 背景

项目设计文档（`ReleaseBaseline`、`TechnicalSolution`、`DemandAnalysis`、`AiProgrammingGuide`）明确把 Spring AI Alibaba 写成 1.0 的 LLM connector 方案。先前为了采用 Spring Boot 4，我们曾尝试改为 Spring AI 2.x + 暂不引入 SAA（ADR 0001），但这会偏离设计文档，也让 1.0 失去 SAA 的 connector 兼容性。

当前事实：

- Spring AI Alibaba 1.1.2.4-security-fix 的 autoconfigure 依赖 **Spring AI 1.1.2** 和 **Spring Boot 3.5.10**。
- SAA 的 Boot 4 适配版本仍为 `2.0.0-M1.1` milestone，没有 GA。
- Spring Boot 3.5.16 是 3.5 线最后一个 OSS 版本；3.5 OSS 已于 2026-06-30 EOL。

## 决策

1. 1.0 保持 **Spring Boot 3.5.16 + Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.4-security-fix + springdoc 2.8.17**。
2. 恢复设计文档中的 SAA 方案表述；不把 Boot 4 / Spring AI 2 作为 1.0 基线。
3. 接受 Boot 3.5 OSS EOL 风险；升级到 Boot 4 的条件是 SAA 的 Boot 4 兼容版本 GA。
4. 在 `docs/engineering/00-versions.md` 记录版本矩阵和 EOL 风险；在 CI 中继续跑 SCA/SBOM。

## 影响

- 1.0 使用 Jackson 2，不引入依赖 Jackson 3 的库。
- Spring AI 不能单独升级到 1.1.8；必须与 SAA 1.1.2.x 的 autoconfigure 对齐。
- `lytgos-provider` 放入 SAA starter，默认 `spring.ai.dashscope.enabled=false`；需要 DashScope/Qwen 时再开启并注入 `DASHSCOPE_API_KEY`。
- Boot 3.5 的 OSS 安全补丁已停止；需要持续关注 CVE，必要时使用商业补丁或提前规划 Boot 4 升级。
- 质量门禁、无 Prometheus、Google 格式优先、P3C 门禁等决策不受影响。

## 备选方案

| 方案 | 结果 | 未采用原因 |
|---|---|---|
| Boot 4.1.3 + Spring AI 2.0.3，暂不引入 SAA | 当前线、稳定 | 偏离设计文档，1.0 失去 SAA 兼容性 |
| Boot 4.x + SAA 2.0.0-M1.1 | 可用但无 GA | milestone，不适合作为 1.0 基线 |
| Boot 3.5.16 + Spring AI 1.1.2 + SAA 1.1.2.4 | 与设计文档一致 | 采用；接受 OSS EOL 风险 |
