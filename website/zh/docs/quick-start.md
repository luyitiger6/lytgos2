# 快速开始

把 LytgOS2 构建起来、配置好，并跑通第一个 Agent。

## 前置条件

- **Java 21+**——运行时基线
- **Maven 3.9+**——从源码构建
- 至少一个 LLM Provider API key（1.0 需要 DeepSeek 或 Kimi）

## 构建

LytgOS2 是 Maven 多模块项目：

```bash
mvn clean package
```

可执行 JAR 位于 `lytgos-boot/target/`。下面的示例用 `lytgos <command>` 作为以下命令的简写：

```bash
java -jar lytgos-boot/target/lytgos-boot-*.jar <command>
```

## 配置 Provider

创建外部 `config/application.yml`，不要提交到版本库。凭证使用环境变量占位符：

```yaml
lytgos:
  root: .lytgos
  providers:
    - name: deepseek
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
    - name: kimi
      api-key: ${MOONSHOT_API_KEY}
      base-url: https://api.moonshot.cn/v1
```

```bash
export DEEPSEEK_API_KEY=sk-...
```

不要提交真实密钥。1.0 没有加密密钥存储；环境变量和外部配置文件就是边界。

## 初始化工作区

```bash
lytgos init
```

这会创建 `.lytgos/` 和默认 Bootstrap 文件。命令是幂等的：已有文件不会被覆盖。查看结果：

```bash
lytgos status
```

## 创建第一个 Agent

一个 Agent 就是一个带 `AGENT.md` 的目录：

```bash
lytgos profile create weather-daily
```

编辑 `.lytgos/agents/weather-daily/AGENT.md`：

```markdown
---
name: weather-daily
description: 每日天气提醒
identity:
  agent_name: Weather
  prompt: |
    You are a weather assistant. Check today’s weather and suggest clothing.
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
  - notify
  - save_memory
  - recall_memory
channels:
  - name: cli
bootstrap:
  - AGENTS.md
  - SOUL.md
  - USER.md
schedules:
  - key: daily
    name: Daily weather
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: Check today’s Beijing weather and send a dressing tip.
settings:
  max_iterations: 10
  max_history_turns: 20
---

You are a weather assistant. In the morning, check the Beijing weather and
send a short dressing suggestion to the `team-lark` notify channel.
```

如果 Agent 使用 `notify`，需要先注册通知渠道（webhook）：

```bash
curl -X POST http://localhost:8989/api/v1/notify-channels \
  -H 'Content-Type: application/json' \
  -d '{"name":"team-lark","type":"webhook","url":"https://open.larksuite.com/open-apis/bot/v2/hook/xxxx","description":"团队飞书 webhook"}'
```

该域名必须在 Sandbox 的 `http.allowed_domains` 中放行。

## 与 Agent 对话

```bash
lytgos chat --profile weather-daily
```

## 启动 REST 服务

```bash
lytgos serve
```

服务默认监听 `8989`：

```bash
curl http://localhost:8989/api/v1/health
```

守护进程模式使用 `lytgos gateway`。

## 1.0 Demo 路径

1.0 的验收 Demo 都走这条链路：

| Demo | 验证能力 | 是否硬性 |
| --- | --- | --- |
| 每日天气 | LLM + ReAct + HTTP + notify + Scheduler + Session 查询 | 是 |
| 每日科技日报 | Agent 目录 + 全局 Skill 软连接 + MCP + Memory + notify + Scheduler | 是 |
| 每日 GitHub 日报 | Agent 目录 + `scripts/` + 专用 Tool/MCP | 可选 |

三个 Demo 都复用同一条 `AgentService.process(...)` 路径，CLI、HTTP 和 cron 触发没有区别。

## 下一步

- [Profile 配置](/zh/docs/profile)——`AGENT.md` 的全部字段
- [工具体系](/zh/docs/tool)——9 个内置 Tool 与 Sandbox
- [REST API](/zh/docs/api)——1.0 的端点范围
- [CLI](/zh/docs/cli)——12 个命令
