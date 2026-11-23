# Quickstart & Validation Guide: LytgOS2 1.0

**Feature**: `001-lytgos-runtime-kernel` | **Date**: 2026-11-22

本指南用于端到端验证 1.0 运行时内核。它只描述**运行与验收步骤**，不包含实现代码；实现细节见 `plan.md`、`data-model.md` 与 `contracts/`。

## 1. 前置条件

- JDK 21+：`java -version` 显示 21 或更高。
- Maven 3.9+：`mvn -v` 使用 JDK 21。
- 至少一个 LLM 账号：DeepSeek 或 Kimi（本指南两者都配置，实际可只配一个）。
- 一个可接收 POST 的 webhook（企业微信/飞书/钉钉群机器人，或测试用 HTTP 端点）。
- 可选：`jq`（格式化 REST 响应）、`sqlite3`（查看审计表）、Python 3（示例 MCP server）。

环境变量：

```bash
export DEEPSEEK_API_KEY="<your-deepseek-key>"   # 可选
export KIMI_API_KEY="<your-kimi-key>"           # 可选
export LYTGOS_WORKSPACE="$PWD/.lytgos"          # 可选，默认 ./.lytgos
```

## 2. 构建

```bash
mvn clean package
```

预期：

- `lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar` 是可执行 fat JAR。
- `mvn` 构建成功；测试结果以实际输出为准，不得在未运行时声称通过。

运行方式（二选一）：

```bash
java -jar lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar <command>
# 或使用仓库根目录的启动脚本
bin/lytgos <command>
```

## 3. 初始化工作区

```bash
bin/lytgos init
```

预期创建 `.lytgos/`、`agents/`、`skills/`、`memory/`、`logs/`、`output/`、Bootstrap 三文件、`MEMORY.md`、`mcp_servers.yaml`。重复执行不覆盖已有文件。

```bash
bin/lytgos status
```

预期显示工作区路径、版本、Profile/Provider/Tool/Session/定时任务数量；不显示密钥。

## 4. Provider 与第一个 Agent

### 4.1 配置 Provider

默认 `application.yaml` 已声明 `deepseek` 与 `kimi`，密钥通过环境变量注入。若使用外部配置，可在 `lytgos-boot/src/main/resources/application.yaml` 或 `--spring.config.additional-location` 中配置：

```yaml
lytgos:
  providers:
    deepseek:
      type: openai-compatible
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:}
      default-model: deepseek-chat
    kimi:
      type: openai-compatible
      base-url: https://api.moonshot.cn/v1
      api-key: ${KIMI_API_KEY:}
      default-model: moonshot-v1-8k
```

```bash
bin/lytgos provider list
```

预期显示 `deepseek` / `kimi`、默认模型、base URL、`KEY: configured|missing`；不显示 key 值。

### 4.2 创建 Agent

```bash
bin/lytgos profile create hello
```

编辑 `.lytgos/agents/hello/AGENT.md`，选择一个已配置 key 的 Provider：

```yaml
---
name: hello
description: 最小对话 Agent
provider:
  name: deepseek          # 或 kimi
  model: deepseek-chat    # 或 moonshot-v1-8k
tools: []
settings:
  max_iterations: 10
  max_history_turns: 20
---
# 任务

用简洁的中文回答问题。
```

```bash
bin/lytgos profile list
bin/lytgos profile show hello
```

### 4.3 多轮对话与持久化

```bash
bin/lytgos chat --profile hello
> 你好，用一句话介绍你自己。
> 继续：你刚才说了什么？
> /quit
```

预期：

- 收到模型回复，Session 历史累积；
- `llm_calls` 有对应记录；
- 重新执行 `bin/lytgos chat --profile hello` 会自动恢复最近 active Session，能看到上下文；
- `bin/lytgos chat --profile hello --new` 会新建 Session；
- `bin/lytgos session list` 默认只显示 active Session。

```bash
sqlite3 .lytgos/lytgos.db "select provider, model, success, duration_ms from llm_calls order by id desc limit 3;"
```

### 4.4 切换 Provider

把 `AGENT.md` 的 `provider.name`/`model` 改为另一个已配置 Provider，重新 `chat`；确认 `llm_calls.provider`/`model` 随之变化。不需要改代码或重新打包。

## 5. ReAct 与 Tool

### 5.1 配置沙箱

编辑 `application.yaml`，加入天气 API 与 webhook 域名：

```yaml
lytgos:
  sandbox:
    file:
      allowed-paths: ["./.lytgos"]
    shell:
      allowed-commands: []
    http:
      allowed-domains: ["api.open-meteo.com", "qyapi.weixin.qq.com"]
```

默认白名单为空；未加入域名时 HTTP Tool 会返回沙箱违规错误，这是预期行为。

### 5.2 天气 Agent

```bash
bin/lytgos profile create weather-demo
```

`.lytgos/agents/weather-demo/AGENT.md`：

```yaml
---
name: weather-demo
description: 查询天气并给出穿搭建议
provider:
  name: deepseek
  model: deepseek-chat
tools: [http_get, notify]
settings:
  max_iterations: 10
  max_history_turns: 20
---
# 任务

查询北京天气并给出穿搭建议；需要推送时调用 notify。
```

创建通知渠道（需要 `lytgos serve` 已启动，见第 7 节）：

```bash
curl -s -X POST http://localhost:8989/api/v1/notify-channels \
  -H 'Content-Type: application/json' \
  -d '{"name":"team-webhook","type":"generic","url":"https://your-webhook.example.com/hook","description":"demo"}'
```

```bash
bin/lytgos chat --profile weather-demo
> 查一下北京天气并告诉我穿什么。
```

预期：Agent 调用 `http_get`，再调用 `notify`；回复包含天气与穿搭建议。检查：

```bash
sqlite3 .lytgos/lytgos.db "select tool_name, success, retry_count, duration_ms from tool_invocations order by id desc limit 5;"
```

### 5.3 沙箱拒绝验证

在 `chat` 中要求 Agent 读取 `../etc/passwd`、执行 `bash`、访问 `https://not-allowed.example.com`。预期：

- Tool 返回安全违规错误；
- `tool_invocations.success=0`；
- Agent 不会崩溃，循环继续或给出最终说明。

## 6. Memory

```bash
bin/lytgos chat --profile hello
> 请记住：我的项目部署在 K8s，回答尽量用中文。
> /quit
```

预期 `save_memory` 被调用（或 Agent 明确保存），`.lytgos/memory/MEMORY.md` 的 `## 核心记忆` 出现条目。

```bash
bin/lytgos chat --profile hello --new
> 我的项目部署在哪里？
```

预期新 Session 的回复引用 K8s 偏好。

```bash
bin/lytgos chat --profile hello
> 从归档记忆里找“芯片”。
```

预期 `recall_memory` 返回命中；无命中时返回空结果，不报错。

## 7. REST API

```bash
bin/lytgos serve
```

另开终端：

```bash
curl -s http://localhost:8989/api/v1/health
curl -s http://localhost:8989/api/v1/info
curl -s http://localhost:8989/v3/api-docs > /tmp/lytgos-openapi.json
```

### 7.1 会话全链路

```bash
SESSION_ID=$(curl -s -X POST http://localhost:8989/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -d '{"profile":"hello","user":"alice"}' | jq -r '.data.sessionId')

curl -s -X POST "http://localhost:8989/api/v1/sessions/$SESSION_ID/messages" \
  -H 'Content-Type: application/json' \
  -d '{"message":"用一句话解释 ReAct。"}'

curl -s "http://localhost:8989/api/v1/sessions/$SESSION_ID"

curl -s -X DELETE "http://localhost:8989/api/v1/sessions/$SESSION_ID"

# 归档后写入应返回 409
curl -s -o /tmp/archived.json -w "%{http_code}\n" \
  -X POST "http://localhost:8989/api/v1/sessions/$SESSION_ID/messages" \
  -H 'Content-Type: application/json' \
  -d '{"message":"还能继续吗？"}'
cat /tmp/archived.json
```

预期：创建返回 `sessionId`；发消息返回 `reply`；查询历史完整；删除后 `status=archived`；再次发消息 409 且错误信封可读。

### 7.2 无状态 invoke

```bash
curl -s -X POST http://localhost:8989/api/v1/agents/hello/invoke \
  -H 'Content-Type: application/json' \
  -d '{"message":"只回答：1+1 等于几？"}'
```

预期：返回 `reply`，响应中没有 `sessionId`；`sqlite3` 查询最近 `llm_calls.session_id is null`；`sessions` 不新增行。

### 7.3 列表与错误

```bash
curl -s http://localhost:8989/api/v1/profiles
curl -s http://localhost:8989/api/v1/tools
curl -s http://localhost:8989/api/v1/memory
curl -s -o /tmp/unknown.json -w "%{http_code}\n" http://localhost:8989/api/v1/agents/not-exist/invoke \
  -H 'Content-Type: application/json' -d '{"message":"hi"}'
cat /tmp/unknown.json
```

预期：统一信封；404 错误可操作；Tool 列表不含 Agent/Skill。

## 8. 定时任务与 gateway

### 8.1 定义一个每分钟运行的任务

在 `hello` 的 `AGENT.md` 中加入：

```yaml
schedules:
  - key: heartbeat
    cron: "0 * * * * *"      # 每分钟第 0 秒
    zone: Asia/Shanghai
    message: 输出一句当前时间问候。
```

重启 `lytgos serve`。

```bash
curl -s http://localhost:8989/api/v2/schedules
curl -s -X POST http://localhost:8989/api/v2/schedules/1/run
curl -s http://localhost:8989/api/v2/schedules/1/executions
curl -s -X PUT http://localhost:8989/api/v2/schedules/1 \
  -H 'Content-Type: application/json' -d '{"enabled":false}'
```

预期：任务列表含 `scheduleId`；手动 run 返回 202；执行历史有记录；停用后不再自动触发（手动 run 仍可执行）。

### 8.2 gateway

停止 `serve`（同一工作区不要同时运行两个进程），然后：

```bash
bin/lytgos gateway
```

预期：进程常驻、**不监听 8989**；到点触发任务并写 `task_executions`；可用另一个终端执行 `bin/lytgos session list` 或 `sqlite3` 查看调度 Session/执行历史。

## 9. 两个硬性 Demo

### 9.1 每日天气

```bash
cp -r examples/agents/daily-weather .lytgos/agents/
bin/lytgos profile show daily-weather
```

- 配置 `api.open-meteo.com` 与 webhook 域名到沙箱；
- 通过 REST 创建 `team-webhook` 渠道；
- 修改 cron 为近期时间或用 `POST /api/v2/schedules/{id}/run` 手动补跑；
- 用 `lytgos chat --profile daily-weather` 手动验证同一条 `AgentService` 链路。

预期：到点自动运行；`http_get` 与 `notify` 各写一条 `tool_invocations`；`GET /api/v1/sessions/{id}` 可查完整对话；手动补跑与自动触发使用同一链路。

### 9.2 每日科技日报

```bash
cp -r examples/agents/daily-tech-news .lytgos/agents/
cp -r examples/skills/tech-digest .lytgos/skills/
ln -s ../../skills/tech-digest .lytgos/agents/daily-tech-news/skills/tech-digest
```

> Windows 创建软链接需要开发者模式或管理员权限；也可用 `mklink /D`（cmd）或 `New-Item -ItemType SymbolicLink`（PowerShell）。软连接必须是相对路径。

- 在 `.lytgos/mcp_servers.yaml` 配置 `tech-news`；
- 先与 Agent 对话保存偏好（如“更关注 AI 和芯片”）；
- 手动或定时运行日报任务；
- 检查 `tool_invocations` 包含 `read_file`（读取 Skill 正文）与 `mcp:tech-news` 工具；回复体现记忆偏好；
- Skill 正文不进入 system prompt，只注入 name/description/路径（可通过测试或 debug 日志验证）。

### 9.3 可选 Demo：GitHub 日报

`examples/agents/daily-github/` 提供目录与 `scripts/` 示例；不纳入 1.0 发布阻塞。验证脚本输出进入上下文、脚本代码本身不进入 prompt。

## 10. 测试与性能验收

```bash
mvn test
```

- 单元/集成测试覆盖：ReAct、Prompt、Memory、Sandbox、Storage、Web、CLI、Scheduler。
- Demo 测试使用 WireMock/本地 MCP，不依赖公网；以实际输出为准。
- 未实际运行的测试不得声称通过。

4 小时压测（人工验收）：

1. 启动 `lytgos serve`，准备 10 个 Agent、100 个并发 Session；
2. 用 k6/JMeter 或仓库 `scripts/perf/` 脚本持续发消息 4 小时；
3. 记录 Session 创建 P99、内部转发开销、错误率、SQLite 锁等待；
4. 目标：P99 ≤ 200ms、内部开销 ≤ 50ms、无丢失消息、进程稳定。

## 11. 常见问题

| 现象 | 处理 |
|---|---|
| `Provider key missing` | 设置对应环境变量，或把 Agent 切换到已配置 Provider |
| `workspace not initialized` | 先执行 `bin/lytgos init` |
| `SandboxViolationException` / 安全违规 | 把路径/可执行文件/域名加入对应白名单；默认白名单为空 |
| `Session 已归档` | 新建 Session；1.0 不支持恢复归档 |
| SQLite `database is locked` | 确认 WAL/busy_timeout；不要同时运行两个写进程（`serve` 与 `gateway` 二选一） |
| MCP server 启动失败 | 检查 `command`/`args`/`env`，查看 server stderr；`tool list` 中应无其工具 |
| 端口冲突 | 修改 `server.port` 或停止占用 8989 的进程 |
| `gateway` 没有 HTTP | 预期行为；gateway 不暴露 REST，管理端点由 `serve` 提供 |
