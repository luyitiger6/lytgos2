# ADR 0003：1.0 不引入 Prometheus / Grafana

- **状态**：已接受
- **日期**：2026-11-22
- **决策者**：项目所有者

## 背景

1.0 基线明确把“Prometheus 指标”“Grafana Dashboard”“trace ID 串联”列为不做项。基础工程需要的是可运行、可排障的最小观测面，而不是完整监控平台。

## 决策

1.0 的观测能力固定为：

- `GET /api/v1/health`
- `GET /api/v1/info`
- 结构化日志（开发 console，生产 ECS JSON + 滚动文件）
- `tool_invocations` / `llm_calls` 审计表

不引入：

- `micrometer-registry-prometheus`
- `/actuator/prometheus`
- Grafana / Prometheus 配置
- 分布式 trace ID 串联

Actuator 只保留 `health` / `info`，不暴露 `env`、`heapdump`、`threaddump`。

## 影响

- 运维通过健康检查、日志和审计表排障；没有指标面板。
- 扩展阶段如要加 Prometheus，必须先修订 `ReleaseBaseline` 和宪章，再改 POM/配置，不能静默添加。
- 本文与 `docs/engineering/02-runtime.md` 保持一致。
