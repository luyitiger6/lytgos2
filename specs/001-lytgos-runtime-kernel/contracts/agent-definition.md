# Agent Definition Contract: LytgOS2 1.0

**Feature**: `001-lytgos-runtime-kernel` | **Source of truth**: `.lytgos/agents/<name>/AGENT.md`

一个目录 = 一个 Agent。`AGENT.md` 的 **frontmatter 是运行时 Profile**，**正文是任务指令**。`AgentLoader.deriveProfile()` 负责解析、校验并注册到 `ProfileRegistry`。

> 注意区分：`AGENTS.md`（复数）是工作区级 Bootstrap 文件；`AGENT.md`（单数）是某个 Agent 的定义文件。

## 1. 目录结构

```text
.lytgos/agents/daily-weather/
├── AGENT.md                  # 必需：frontmatter + 任务正文
├── skills/                   # 可选：指向 ../../skills/<skill> 的相对软连接
│   └── weather-format -> ../../skills/weather-format
├── scripts/                  # 可选：脚本，经 shell Tool 白名单执行
└── REFERENCE.md              # 可选：参考资料，经 read_file 读取
```

- Skill 绑定**不写入 frontmatter**；`skills/` 软连接集合是唯一绑定真相源。
- 目录名即 Agent 名；frontmatter 的 `name` 必须与目录名一致。
- `ContextLoader` 每轮重新读取 `AGENT.md` 正文和 `skills/`，不缓存；修改/重新绑定后下一轮生效。

## 2. Frontmatter 完整结构

```yaml
---
name: daily-weather
description: 每日天气与穿搭建议

identity:
  agent_name: 天气助手
  prompt: 你是一个简洁、可靠的天气与穿搭建议助手。

provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7

tools:
  - http_get
  - notify

mcp_servers: []

channels:
  - name: cli

bootstrap:
  - AGENTS.md
  - SOUL.md
  - USER.md

schedules:
  - key: morning-weather
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: 查询北京今天的天气并给出穿搭建议，然后推送到 team-webhook。
    display_name: 每日天气
    enabled: true

settings:
  max_iterations: 10
  max_history_turns: 20
---
# 任务

每天早上查询北京天气，给出简洁的穿搭建议，并调用 notify 推送到 team-webhook。
```

### 2.1 字段表

| 字段 | 类型 | 必填 | 默认 | 校验/说明 |
|---|---|---|---|---|
| `name` | string | 是 | — | 必须等于目录名；`^[a-z0-9][a-z0-9-]{0,62}$` |
| `description` | string | 是 | — | 非空 |
| `identity.agent_name` | string | 否 | `name` | 展示名 |
| `identity.prompt` | string | 否 | 空 | 附加人格/系统提示 |
| `provider.name` | string | 是 | — | 必须存在于 ProviderRegistry（`deepseek`/`kimi` 等） |
| `provider.model` | string | 是 | — | 非空；覆盖 Provider 默认模型 |
| `provider.temperature` | double | 否 | Provider 默认 | `0.0–2.0` |
| `tools` | list<string> | 否 | `[]` | 每个名称必须已注册；未列出则不可用 |
| `mcp_servers` | list<string> | 否 | `[]` | 必须存在于 `.lytgos/mcp_servers.yaml` |
| `channels` | list<object> | 否 | `[{name: cli}]` | 1.0 仅支持 `cli` |
| `bootstrap` | list<string> | 否 | `[AGENTS.md, SOUL.md, USER.md]` | 文件相对工作区根；缺失告警并跳过 |
| `schedules` | list<object> | 否 | `[]` | 见 2.2 |
| `settings.max_iterations` | int | 否 | `10` | `1–50` |
| `settings.max_history_turns` | int | 否 | `20` | `1–100`；一轮 = 用户消息 + 最终回复 |
| 未知字段 | — | — | — | 记录告警并忽略 |

### 2.2 `schedules` 元素

| 字段 | 类型 | 必填 | 默认 | 校验/说明 |
|---|---|---|---|---|
| `key` | string | 是 | — | 同 Profile 唯一；`^[a-z0-9][a-z0-9-]{0,62}$` |
| `cron` | string | 是 | — | Spring 6 字段：秒 分 时 日 月 周 |
| `zone` | string | 否 | 系统时区 | IANA zone |
| `message` | string | 是 | — | 到点发给 Agent 的用户消息 |
| `display_name` | string | 否 | `key` | 展示名 |
| `enabled` | boolean | 否 | `true` | 管理端可切换 |

- 1.0 只从 `AGENT.md` 读取 cron 定义；运行时只能启停，不能增删改 cron。
- 修改 `schedules` 后需重启（或触发重新加载）生效。
- 调度 Session：channel/user 均为 `scheduler`，同一 Profile 复用最近 active Session。

## 3. Skill 绑定

### 3.1 Skill 实体

```text
.lytgos/skills/weather-format/
├── SKILL.md
├── references/
└── scripts/
```

`SKILL.md` frontmatter：

```yaml
---
name: weather-format
description: 把天气 API 结果整理成中文穿搭建议
---
（正文/参考/脚本按需读取）
```

- `name` 必须等于目录名；`description` 必填。
- 正文、`references/`、`scripts/` 不预载到 prompt。

### 3.2 Agent 绑定

```bash
cd .lytgos/agents/daily-weather/skills
ln -s ../../skills/weather-format weather-format
```

- 必须是**相对软连接**，目标位于 `.lytgos/skills/` 内。
- 拒绝绝对路径、`..` 逃逸、指向普通文件/工作区外的链接。
- 每轮只注入：Skill `name`、`description`、Agent 本地绝对路径。
- 模型经 `read_file`/`shell` 读取正文/脚本；对应路径必须在沙箱白名单内。

## 4. Bootstrap

- 默认 `AGENTS.md`、`SOUL.md`、`USER.md`，位于工作区根 `.lytgos/`。
- 内容全量进入 system prompt；`USER.md` 是用户手写初始设定，`MEMORY.md` 是 Agent 通过 `save_memory` 写入的成长记录。
- 缺失文件记录告警，不阻断 Agent；`init` 会生成默认模板。

## 5. 加载与校验行为

- 启动时扫描 `.lytgos/agents/*/AGENT.md`，逐个派生 Profile。
- 单个 Agent 校验失败：记录错误日志，不加入 `ProfileRegistry`，不阻断其他 Agent。
- 校验项：
  - 目录名 = `name`；名称格式合法；
  - Provider 存在；模型非空；temperature 范围；
  - 每个 Tool 已注册；每个 MCP server 存在；
  - Skill 软连接合法；
  - Schedule key 唯一、cron/zone 合法；
  - Bootstrap 文件存在（缺失仅告警）。
- 未知 Channel、未知顶层字段：告警；不影响已识别字段。
- `AGENT.md` 正文可以为空（仅 frontmatter），但必须有至少一个 `description` 与正文标题。

## 6. 两个 1.0 示例

### 6.1 每日天气（光杆 AGENT.md）

```yaml
---
name: daily-weather
description: 每日天气与穿搭建议
provider:
  name: deepseek
  model: deepseek-chat
tools: [http_get, notify]
schedules:
  - key: morning-weather
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: 查询北京天气并给出穿搭建议，然后推送到 team-webhook。
settings:
  max_iterations: 10
  max_history_turns: 20
---
# 任务

每天查询北京天气，给出简洁穿搭建议，并调用 notify 推送。
```

### 6.2 每日科技日报（AGENT.md + Skill 软连接 + MCP + Memory）

```yaml
---
name: daily-tech-news
description: 每日科技日报，体现用户关注方向
provider:
  name: kimi
  model: moonshot-v1-8k
tools: [recall_memory, save_memory, notify, read_file]
mcp_servers: [tech-news]
schedules:
  - key: morning-digest
    cron: "0 30 8 * * *"
    zone: Asia/Shanghai
    message: 生成今日科技日报并推送到 team-webhook，优先关注我记住的方向。
---
# 任务

1. 用 recall_memory 查用户关注方向；
2. 通过 tech-news MCP 拉取当日科技新闻；
3. 读取已绑定的 tech-digest Skill 正文；
4. 汇总为中文日报，调用 notify 推送。
```

对应 `skills/tech-digest -> ../../skills/tech-digest` 软连接与 `.lytgos/mcp_servers.yaml` 中的 `tech-news` 配置，见 [skill 绑定](#32-agent-绑定) 与 [MCP 配置](./mcp-config.md)。
