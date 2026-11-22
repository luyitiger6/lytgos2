# CLI 参考

CLI 是 `lytgos <command>` 入口（Picocli）。1.0 共有 12 个命令。

## 命令（12 个）

| 类别 | 命令 | 用途 |
| --- | --- | --- |
| 启动与状态 | `lytgos init` | 创建 `.lytgos/` 工作区与默认 Bootstrap 文件 |
| 启动与状态 | `lytgos status` | 查看工作区与数据文件状态 |
| 启动与状态 | `lytgos chat --profile <name>` | 与 Agent 交互对话 |
| 启动与状态 | `lytgos serve` | 启动 REST API 服务（默认 8080） |
| 启动与状态 | `lytgos gateway` | 守护进程模式常驻运行 |
| Profile | `lytgos profile list` | 列出 Agent 目录 / Profile |
| Profile | `lytgos profile create <name>` | 创建 Agent 目录与 `AGENT.md` |
| Profile | `lytgos profile show <name>` | 查看 Profile |
| Profile | `lytgos profile delete <name>` | 删除 Agent 目录 |
| 查询 | `lytgos provider list` | 列出已配置 Provider |
| 查询 | `lytgos tool list` | 列出已注册 Tool |
| 查询 | `lytgos session list` | 列出 Session |

`lytgos agent import` 属于扩展阶段，不计入这 12 个命令。

## 轻命令与运行时命令

- **轻命令**（`init`、`status`、`profile list/create/show/delete`）直接操作文件，不启动 Spring。
- **运行时命令**（`chat`、`serve`、`gateway`、`provider list`、`tool list`、`session list`）需要运行时，会启动 Spring。

## 工作区

```text
.lytgos/
├── agents/            # 每个子目录一个 Agent（AGENT.md + skills/ + scripts/）
├── skills/            # 全局 Skill 库
├── memory/
│   └── MEMORY.md      # 长期记忆
├── sessions/          # Session 数据
├── logs/              # 结构化日志
├── output/            # Agent 产出物
├── mcp_servers.yaml   # MCP 配置
├── AGENTS.md          # Bootstrap：项目级行为说明
├── SOUL.md            # Bootstrap：默认人格
├── USER.md            # Bootstrap：用户偏好
└── lytgos.db          # SQLite 数据库
```

## 示例

```bash
# 初始化工作区
lytgos init

# 查看工作区状态
lytgos status

# 创建 Agent 并对话
lytgos profile create weather-daily
lytgos chat --profile weather-daily

# 服务与守护进程模式
lytgos serve
lytgos gateway

# 查看运行时状态
lytgos profile list
lytgos provider list
lytgos tool list
lytgos session list
```

## 工作区根目录

默认工作区根是 `.lytgos`，可通过以下方式覆盖：

- `-Dlytgos.root=<path>`：Java 系统属性
- `LYTGOS_ROOT=<path>`：环境变量

轻命令直接读取 `LYTGOS_ROOT`；运行时命令还会通过 Spring relaxed binding 解析。

## 1.0 边界

CLI 不包含 `agent import`、运行时 cron 增删改、Web 管理台或认证。分阶段能力见[路线图](/zh/docs/roadmap)。
