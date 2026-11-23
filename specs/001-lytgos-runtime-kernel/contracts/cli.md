# CLI Contract: LytgOS2 1.0

**Feature**: `001-lytgos-runtime-kernel` | **Command**: `lytgos <command>`

## 1. 入口与全局行为

- 发布物是一个 Spring Boot fat JAR；`bin/lytgos`（Unix）与 `bin/lytgos.cmd`（Windows）只是启动脚本，执行 `java -jar lytgos-boot-1.0.0-SNAPSHOT.jar "$@"`。
- `java -jar lytgos-boot-...jar` 无参数时等价于 `lytgos serve`。
- `lytgos --help`、`lytgos --version` 不启动 Spring 上下文，直接返回。
- Picocli 提供每个子命令的 `--help`；帮助文本必须包含必填参数、默认值和示例。
- 退出码：`0` 成功；`1` 运行时/配置错误；`2` 用法错误（Picocli 默认）；`3` Provider/外部依赖错误。
- 错误输出到 stderr，格式为 `ERROR: <可操作信息>`；不输出堆栈，不回显密钥。
- 默认工作区 `./.lytgos`，可用 `LYTGOS_WORKSPACE` 或 `--workspace`（全局选项）覆盖。
- 需要运行时上下文的命令：`chat`、`serve`、`gateway`、`tool list`、`session list`；文件/配置命令：`init`、`status`、`profile ...`、`provider list`。

## 2. 12 个命令

### 2.1 `lytgos init`

初始化工作区，幂等，不覆盖已有文件。

```bash
lytgos init [--workspace <dir>]
```

创建：

```text
.lytgos/
├── agents/  skills/  memory/  sessions/  logs/  output/
├── AGENTS.md  SOUL.md  USER.md
├── memory/MEMORY.md          # 含 ## 核心记忆 / ## 归档记忆
├── mcp_servers.yaml          # servers: {}
└── lytgos.db                 # 空文件，首次运行时 Flyway 建表
```

输出：工作区路径、创建的目录/文件数量、下一步提示（`lytgos profile create <name>`）。

### 2.2 `lytgos status`

```bash
lytgos status [--workspace <dir>]
```

输出：

- 产品/版本、工作区路径、是否已初始化；
- Profile 数量、Provider 名称、已注册 Tool 数量；
- Session 总数/active 数、定时任务数量；
- 最近一次启动模式（若可读）；不显示密钥或完整 API key。

### 2.3 `lytgos chat`

```bash
lytgos chat [--profile <name>] [--new] [--workspace <dir>]
```

- `--profile` 省略时：仅有一个 Agent 则使用它；多个则报错并列出可选值；没有则提示先 `lytgos profile create`。
- 会话身份：channel=`cli`，user=当前 OS 用户名（取不到用 `local`），profile=所选 Agent。
- 默认自动恢复该 Agent 最近一个 `active` Session（按 `last_active_at`）；没有则新建。
- `--new`：强制新建 Session，即使已有 active。
- 交互：`> ` 提示符读取一行；`/quit` 退出；空行忽略；`Ctrl+C` 优雅退出。
- 每次输入调用 `AgentService.process` 并打印最终回复；Tool 调用过程只打印摘要，不打印完整参数/结果（可用日志查看）。
- 不启动 Web、不启动 Scheduler。
- 错误：Provider 缺 key/不可用、Agent 配置非法、Session 归档等 → 打印可操作错误，进程保持可用。

### 2.4 `lytgos serve`

```bash
lytgos serve [--workspace <dir>]
```

- 启动 Spring MVC + virtual threads + REST + `AgentScheduler`；默认端口 8989（`server.port`）。
- 要求工作区已初始化；未初始化时给出“先运行 lytgos init”的错误并退出码 1。
- 前台阻塞，直到 SIGINT/SIGTERM。
- 唯一暴露 HTTP 的模式；不得与 `gateway` 在同一工作区以两个独立进程同时运行。

### 2.5 `lytgos gateway`

```bash
lytgos gateway [--workspace <dir>]
```

- 无 HTTP 的常驻守护进程：启动 `AgentScheduler` 与未来配置的常驻 Channel；不监听 REST 端口。
- 同样要求工作区已初始化；前台阻塞。
- 定时任务的查询/手动执行由 `lytgos serve` 的 v2 端点提供，或通过 CLI 手动补跑。

### 2.6 `lytgos profile list`

```bash
lytgos profile list
```

输出表格：`NAME`、`PROVIDER`、`MODEL`、`TOOLS`、`SCHEDULES`、`DESCRIPTION`。

### 2.7 `lytgos profile create`

```bash
lytgos profile create <name>
```

- `name` 匹配 `^[a-z0-9][a-z0-9-]{0,62}$`；目录已存在则报错，不覆盖。
- 生成 `.lytgos/agents/<name>/AGENT.md` 最小模板：

```yaml
---
name: <name>
description: 描述这个 Agent 的用途
provider:
  name: deepseek
  model: deepseek-chat
tools: []
settings:
  max_iterations: 10
  max_history_turns: 20
---
# <name>

描述这个 Agent 要完成的任务。
```

- 同时创建空的 `skills/` 与 `scripts/` 目录。
- 不创建 Provider 配置；若 `deepseek` 未配置，首次对话会给出清晰错误。

### 2.8 `lytgos profile show`

```bash
lytgos profile show <name>
```

输出 `AGENT.md` 原文；不存在 → 错误，退出码 1。

### 2.9 `lytgos profile delete`

```bash
lytgos profile delete <name> [--yes]
```

- 删除整个 `.lytgos/agents/<name>/` 目录；默认交互确认，`--yes` 跳过。
- 不删除 Session、审计、长期记忆或其他 Agent。
- 不存在 → 错误，退出码 1。

### 2.10 `lytgos provider list`

```bash
lytgos provider list
```

输出表格：`NAME`、`TYPE`、`DEFAULT MODEL`、`BASE URL`、`KEY`（`configured` / `missing`，不显示值）。

### 2.11 `lytgos tool list`

```bash
lytgos tool list
```

输出表格：`NAME`、`SOURCE`（`builtin` / `mcp:<server>` / `java`）、`DESCRIPTION`。

- Agent 目录与 Skill 不出现在列表中。
- 需要运行时注册表；启动 Web 类型为 NONE 的上下文。

### 2.12 `lytgos session list`

```bash
lytgos session list [--status active|archived|all] [--limit <n>]
```

- 默认 `--status active`；归档 Session 需显式筛选。
- 默认 `--limit 20`，按 `last_active_at` 倒序。
- 输出表格：`SESSION ID`、`PROFILE`、`USER`、`STATUS`、`LAST ACTIVE`、`MESSAGES`。
- 需要存储；启动 Web 类型为 NONE 的上下文。

## 3. 示例

```bash
lytgos init
lytgos profile create daily-weather
lytgos profile show daily-weather
lytgos provider list
lytgos tool list
lytgos chat --profile daily-weather
lytgos chat --profile daily-weather --new
lytgos session list
lytgos session list --status archived --limit 50
lytgos serve
lytgos gateway
```

## 4. 兼容性

- 命令名、参数与输出字段是 1.0 契约；新增参数不得改变默认行为。
- `lytgos agent import`、`lytgos serve --sse` 等属于扩展阶段，1.0 不提供。
- 所有命令必须提供 `--help`；验收中不存在无文档命令。
