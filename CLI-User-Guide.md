# LytgOS2 CLI 用户手册

**适用版本**：LytgOS2 1.0  
**最后更新**：2026-11-23  
**适用范围**：`lytgos` 命令行、CLI 会话、工作区与 AgentProfile 管理

---

## 1. 这份手册适合谁

这份手册面向需要在本地使用 LytgOS2 的使用者，覆盖：

- 第一次初始化工作区；
- 在终端里和 Agent 对话；
- 管理 AgentProfile；
- 查看 Provider、Tool、Session 状态；
- 启动 Web Service 或守护进程骨架；
- 排查常见的启动、端口、密钥和会话问题。

不要求你了解 Spring 或 Agent 引擎内部实现。

---

## 2. 前提与运行方式

### 2.1 JDK

需要 JDK 21 或更高版本。确认：

```powershell
java -version
```

如果项目使用 Maven Wrapper 构建，Maven 本身也会使用 `JAVA_HOME` 指向的 JDK 21+。

### 2.2 构建可执行 JAR

在仓库根目录执行：

```powershell
.\mvnw.cmd -DskipTests package
```

Git Bash / macOS / Linux：

```bash
./mvnw -DskipTests package
```

产物：

```text
lytgos-boot\target\lytgos-boot-1.0.0-SNAPSHOT.jar
```

后文用 `$JAR` 表示这个文件：

```powershell
$JAR = "<仓库路径>\lytgos-boot\target\lytgos-boot-1.0.0-SNAPSHOT.jar"
```

### 2.3 两种运行方式

**方式 A：直接运行 JAR**

```powershell
java -jar $JAR init
java -jar $JAR chat
```

**方式 B：使用 `lytgos` 快捷命令**

第一次以 JAR 方式运行任意命令时，程序会自动安装启动器：

- Windows：写入 `%USERPROFILE%\.local\bin\lytgos.cmd`，并把该目录加入 **用户级** `Path`；
- macOS / Linux：写入 `~/.local/bin/lytgos`，并在已有的 `~/.profile`、`~/.bashrc`、`~/.zshrc`、`~/.zprofile` 中追加带标记的 PATH 块；都不存在时创建 `~/.profile`；
- 不改系统 PATH，不需要管理员权限；
- 生成的 Windows 脚本会执行 `chcp 65001`，并给 Java 设置 UTF-8 输出参数。

安装完成后，**新开一个终端**即可直接使用：

```powershell
lytgos init
lytgos chat
```

当前终端可能还看不到新 PATH，重新打开终端或重新登录即可。

如果不想自动修改 PATH，可以设置：

```powershell
$env:LYTGOS_NO_LAUNCHER_INSTALL = "1"
```

安装器只在从 JAR 运行时工作；从 IDE/`target/classes` 启动时会跳过。若自动安装失败，命令本身仍会继续执行，只输出警告。

只有在自动安装不可用时，才需要手工创建启动脚本。Windows 示例：

```bat
@echo off
set "LYTGOS_HOME=<仓库路径>"
set "JAR=%LYTGOS_HOME%\lytgos-boot\target\lytgos-boot-1.0.0-SNAPSHOT.jar"
java -jar "%JAR%" %*
exit /b %ERRORLEVEL%
```

Git Bash 示例：

```sh
#!/usr/bin/env sh
JAR="<仓库路径>/lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar"
exec java -jar "$JAR" "$@"
```

> 后文命令统一写 `lytgos ...`。如果没有安装快捷命令，把 `lytgos` 替换成 `java -jar $JAR` 即可。

### 2.4 CLI 文案语言

CLI 自己的帮助、状态、错误默认中文；如果终端编码无法保证正常显示中文，会自动整体回退英文，避免乱码。可以显式覆盖：

```powershell
$env:LYTGOS_LANG = "zh"   # 强制中文
$env:LYTGOS_LANG = "en"   # 强制英文
```

注意：CLI 文案语言和对话语言是两件事。对话内容由模型按用户当前语言回复，不受 `LYTGOS_LANG` 限制。

---

## 3. 快速开始

```powershell
# 1. 建一个独立测试目录（推荐，避免和仓库自己的 .lytgos 混用）
New-Item -ItemType Directory -Force D:\lytgos-demo | Out-Null
Set-Location D:\lytgos-demo

# 2. 初始化工作区
lytgos init

# 3. 看一眼状态
lytgos status

# 4. 和默认 Agent 对话
lytgos chat

# 5. 对话结束后查看会话
lytgos session list
```

`chat` 需要可用的模型密钥。默认 Profile 使用 DeepSeek，请先确认当前终端能看到：

```powershell
if ($env:DEEPSEEK_API_KEY) { "DEEPSEEK_API_KEY 已设置" } else { "DEEPSEEK_API_KEY 未设置" }
```

显示“未设置”时可以临时设置：

```powershell
$env:DEEPSEEK_API_KEY = "<你的密钥>"
```

不要把真实密钥写进 `AGENT.md`、Profile YAML 或提交到 Git。

---

## 4. 命令总览

根命令直接包含 5 个叶子命令和 4 个父命令，展开后共 12 个叶子命令：

```text
lytgos
├── init
├── status
├── chat        [--profile <name>]
├── serve
├── gateway
├── profile
│   ├── list
│   ├── create  <name>
│   ├── show    <name>
│   └── delete  <name>
├── provider
│   └── list
├── tool
│   └── list
└── session
    └── list
```

查看根帮助：

```powershell
lytgos --help
```

查看某个命令的帮助：

```powershell
lytgos chat --help
lytgos profile --help
lytgos profile create --help
```

查看版本：

```powershell
lytgos --version
```

输出形如：

```text
LytgOS2 1.0.0
```

---

## 5. 轻命令与重命令

CLI 命令分为两类。判断标准是：**是否需要启动完整的 Agent 运行时（Spring、SQLite、Provider、ReAct 引擎）**。

| 类别 | 命令 | 启动 Spring | 会调模型吗 |
|---|---|---|---|
| 轻命令 | `init`、`status`、`profile *`、`provider list`、`tool list`、`session list` | 否 | 否 |
| 重命令 | `chat` | 是 | 每一轮用户输入都会 |
| 重命令 | `serve` | 是 | 收到消息请求时才会 |
| 重命令 | `gateway` | 是 | 未来收到渠道消息时才会；当前是骨架 |

轻命令直接读写文件或 SQLite，通常很快返回；重命令启动时会看到 Spring 日志，例如：

```text
Starting LytgosCli
Finished Spring Data repository scanning ... Found 3 JPA repository interfaces
```

> 启动 `serve` 本身不会调用模型。只有真正发出一条对话消息，才会产生 `llm_calls` 审计记录。

---

## 6. 命令详解

### 6.1 `lytgos init`

**用途**：初始化工作区。

```powershell
lytgos init
```

**行为**：

- 在当前目录创建 `.lytgos/`；
- 创建 `agents`、`skills`、`memory`、`sessions`、`logs`、`profiles` 目录；
- 创建 `AGENTS.md`、`SOUL.md`、`USER.md`、`MEMORY.md`、`mcp_servers.yaml`；
- 创建默认 AgentProfile `profiles/default.yaml`；
- 已存在的文件不会覆盖；
- 不会创建数据库，不会启动 Spring，不会启动服务。

**示例输出**：

```text
工作区已初始化: D:\lytgos-demo\.lytgos
  已创建: .lytgos\agents
  已创建: .lytgos\skills
  ...
  已创建: .lytgos\profiles\default.yaml
```

第二次执行时，所有条目显示“已存在”。

**退出码**：

| 情况 | 退出码 |
|---|---:|
| 初始化成功，或文件已存在 | 0 |
| 目录/文件创建失败 | 1 |

### 6.2 `lytgos status`

**用途**：查看工作区、数据库、Profile、Provider 和会话数量。

```powershell
lytgos status
```

**示例输出（尚未启动过重命令）**：

```text
工作区: D:\lytgos-demo\.lytgos
数据库: 未初始化
AgentProfile: 1
Provider: 2
会话: 未初始化
```

第一次执行 `chat`、`serve` 或 `gateway` 后，Flyway 会创建数据库，状态变为：

```text
数据库: 已初始化
会话: 1
```

**说明**：

- 工作区不存在时仍然输出摘要，不会抛堆栈；
- `Provider` 数量来自 classpath 中的 `application.yaml`；
- 会话数量通过只读方式查询 SQLite，不创建数据库或表；
- 退出码 0 表示查询完成；读取配置或数据库失败时退出码 1。

### 6.3 `lytgos chat`

**用途**：在终端里和 Agent 交互式对话。

```powershell
lytgos chat
lytgos chat --profile weather
lytgos chat -p weather
```

**参数**：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `--profile` / `-p` | `default` | 使用的 AgentProfile 名称 |

**行为**：

1. 检查 `profiles/<name>.yaml` 是否存在；不存在直接报错，不启动 Spring；
2. 启动 Spring（不创建 Web 服务器）；
3. 按 `channel=cli`、当前系统用户名、Profile 名称获取或创建会话；
4. 显示 `> ` 提示符，读取一行输入；
5. 把输入交给 `AgentService`，由 ReAct 引擎处理；
6. 打印最终回复；
7. 输入空行会跳过；
8. 输入 `/quit` 或输入流结束（EOF）时退出；
9. 单轮 Agent 异常会打印 `错误: ...` 并继续接受输入；最终退出码为 1。

**示例**：

```text
> 你好，请只回复：收到
收到
> 我最开始说的第一句话是什么？只输出那句话。
你好，请只回复：收到
> /quit
```

**会话身份**：

- 渠道固定为 `cli`；
- 用户取当前系统用户名（`user.name`）；
- 默认 Profile 为 `default`；
- 会话标识形如 `cli:<用户名>:default`。

同一组合在多次 `chat` 之间复用同一个会话，历史会自动带入后续对话。

**数据库**：

如果工作区还没有 `lytgos.db`，执行 `chat` 时会由 Flyway 创建，包含 `llm_calls`、`tool_invocations`、`sessions` 等表。

**注意**：

- 不要同时运行两个写同一个 `.lytgos/lytgos.db` 的 `chat`/`serve` 进程，SQLite 可能出现写锁；
- 想并行测试时，换一个目录作为工作区。

### 6.4 `lytgos serve`

**用途**：启动 Web Service。

```powershell
lytgos serve
```

**行为**：

- 启动 Spring MVC；
- 默认监听 **8989**；
- 阻塞当前终端，直到 `Ctrl+C`；
- 启动时执行 Flyway 迁移，创建或升级 `.lytgos/lytgos.db`；
- 与 `chat` 共享同一份 Profile 和 Session 存储。

**当前可用端点**：

```text
GET /api/v1/health
GET /api/v1/info
GET /v3/api-docs
```

示例：

```powershell
curl.exe -i http://localhost:8989/api/v1/health
curl.exe -i http://localhost:8989/api/v1/info
curl.exe -o NUL -w "%{http_code}`n" http://localhost:8989/v3/api-docs
```

**换端口**：

`serve` 没有 `--port` 参数。可以用 JVM 系统属性覆盖：

```powershell
java -Dserver.port=9000 -jar $JAR serve
```

**注意**：

- 端口被占用时启动失败，错误信息会提示端口冲突；
- 停止用 `Ctrl+C`；
- 当前 Web 端点只有 `health`、`info`、OpenAPI，Agent 会话/调用端点尚未接入，因此通过 HTTP 暂时还不能触发模型对话。

### 6.5 `lytgos gateway`

**用途**：启动守护进程骨架。

```powershell
lytgos gateway
```

**行为**：

- 启动 Spring（不创建 Web 服务器）；
- 注册关闭钩子后阻塞，直到 `Ctrl+C`；
- 与 `chat`、`serve` 共享同一份 Profile 和 Session 存储；
- 1.0 不挂载 IM 渠道，实际渠道属于扩展能力。

示例输出：

```text
守护进程已启动，按 Ctrl+C 停止
```

### 6.6 `lytgos profile`

AgentProfile 管理命令组。

#### `lytgos profile list`

```powershell
lytgos profile list
```

列出 `profiles/` 下的 YAML 文件：

```text
NAME                     PROVIDER     MODEL
default                  deepseek     deepseek-chat
weather                  deepseek     deepseek-chat
```

- 没有 Profile 时输出 `暂无 AgentProfile`，退出码 0；
- 单个文件解析失败时给出警告，但仍然列出名称。

#### `lytgos profile create <name>`

```powershell
lytgos profile create weather
```

- 名称只允许字母、数字、下划线、连字符：`[A-Za-z0-9_-]+`；
- 生成 `profiles/weather.yaml`；
- 默认使用 `deepseek` / `deepseek-chat`；
- 文件已存在时返回退出码 1，不覆盖；
- 名称非法或路径越界时返回退出码 1。

生成的模板：

```yaml
name: weather
description: 默认 AgentProfile，由 lytgos init 创建
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
settings:
  max_iterations: 10
  max_history_turns: 20
```

#### `lytgos profile show <name>`

```powershell
lytgos profile show weather
```

原样输出 YAML 内容。文件不存在或名称非法时退出码 1。

#### `lytgos profile delete <name>`

```powershell
lytgos profile delete weather
```

删除对应 YAML。没有交互确认，便于脚本使用；文件不存在时退出码 1。

> `profile list` 会识别 `.yaml` 和 `.yml`；`chat --profile` 当前按 `<name>.yaml` 查找。建议统一使用 `profile create`，它会生成 `.yaml`。

### 6.7 `lytgos provider list`

```powershell
lytgos provider list
```

读取 classpath 中 `application.yaml` 的 `lytgos.providers`，只显示名称和连接地址：

```text
NAME         BASE URL
deepseek     https://api.deepseek.com
kimi         https://api.moonshot.cn/v1
```

**安全约定**：

- 不读取、不解析、不输出 `api-key`；
- `ProviderView` 类型本身没有密钥字段；
- 凭证缺失不会导致列表失败；
- 该命令不会测试 Key 是否有效，也不会请求模型。

### 6.8 `lytgos tool list`

```powershell
lytgos tool list
```

当前 Tool 能力尚未接入，固定输出：

```text
暂无已注册工具（Tool 能力尚未接入）
```

命令不启动 Spring，退出码 0。

### 6.9 `lytgos session list`

```powershell
lytgos session list
```

以只读方式查询 `.lytgos/lytgos.db` 的 `sessions` 表，按最后活跃时间倒序显示：

```text
SESSION ID                               PROFILE      CHANNEL  USER         STATUS   LAST ACTIVE
cli:demo:default                         default      cli      demo         ACTIVE   2026-11-23T08:00:00Z
```

- 数据库、`sessions` 表或数据不存在时输出 `暂无会话`，退出码 0；
- 不会创建数据库或表；
- 数据库损坏等读取失败时退出码 1；
- 该命令不显示消息数量；需要看消息内容时使用 `sqlite3` 或后续 Web API。

可选查询：

```powershell
sqlite3 .lytgos\lytgos.db "select session_id, status, json_array_length(messages_json) from sessions;"
sqlite3 .lytgos\lytgos.db "select json_extract(value,'$.role') || ' | ' || json_extract(value,'$.content') from sessions, json_each(sessions.messages_json);"
```

---

## 7. 工作区

默认工作区是当前目录下的 `.lytgos/`：

```text
.lytgos/
├── agents/
├── skills/
├── memory/
├── sessions/
├── logs/
├── profiles/
│   └── default.yaml
├── AGENTS.md
├── SOUL.md
├── USER.md
├── MEMORY.md
├── mcp_servers.yaml
└── lytgos.db          # 第一次执行重命令后由 Flyway 创建
```

要点：

- 工作区跟随**当前目录**，所以不同项目可以有独立工作区；
- `.lytgos/` 已被 `.gitignore` 忽略，不要把会话数据提交到 Git；
- `init` 只创建目录和占位文件；
- 第一次执行 `chat` / `serve` / `gateway` 时才创建 `lytgos.db`；
- `.lytgos/logs` 可用于存放自己重定向的运行日志。

### 重置工作区

如果需要从头开始：

```powershell
# 先停止 chat/serve/gateway 进程
Remove-Item -Recurse -Force .lytgos
lytgos init
```

> 这会删除该工作区的 Profile、会话历史和审计记录，请先备份。

---

## 8. Session 与会话身份

会话标识只由 `SessionManager` 内部生成：

```text
session_id = channel + ":" + user + ":" + profileName
```

例如：

```text
cli:demo:default
```

规则：

- 同一 channel/user/profile 组合始终对应同一个会话；
- 任一维度不同就是不同会话；
- 调用方（CLI、未来的 Web、定时任务）只提供三元组，不自行拼接字符串；
- `chat` 每次启动都会按同一三元组查找已有会话，并恢复完整历史；
- 输入 `/quit` 退出后，历史已经写入 SQLite，下次继续。

数据库中的 `sessions` 表主要列：

| 列 | 说明 |
|---|---|
| `session_id` | 会话标识，主键 |
| `profile_name` | AgentProfile 名称 |
| `channel` | 接入渠道，CLI 固定为 `cli` |
| `user_id` | 用户标识 |
| `messages_json` | 完整消息历史 JSON |
| `status` | `ACTIVE` / `ARCHIVED`，1.0 只写入 `ACTIVE` |
| `created_at` | 创建时间 |
| `last_active_at` | 最后活跃时间 |
| `archived_at` | 归档时间，当前为空 |

除了会话，重命令还会写入审计表：

- `llm_calls`：每次模型调用成功或失败都记录；
- `tool_invocations`：工具执行记录；当前 Tool 未接入，因此通常为空。

---

## 9. AgentProfile

AgentProfile 定义“用哪个 provider、哪个模型、怎么运行”。

默认文件：`.lytgos/profiles/default.yaml`

```yaml
name: default
description: 默认 AgentProfile，由 lytgos init 创建
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
settings:
  max_iterations: 10
  max_history_turns: 20
```

说明：

- `provider.name` 必须是 `application.yaml` 中声明过的 provider；
- `provider.model` 是模型名称；
- `temperature` 可省略，默认 0.7；
- `max_iterations` 控制 ReAct 最大轮数，默认 10；
- `max_history_turns` 控制注入历史的最大轮数，默认 20；
- Profile 加载支持 `${ENV_VAR}` 和 `${ENV_VAR:default}` 环境变量占位符；
- **不要把明文密钥写进 Profile**。

创建后使用：

```powershell
lytgos chat --profile weather
```

---

## 10. Provider 与密钥

`lytgos-boot/src/main/resources/application.yaml` 中声明 Provider：

```yaml
lytgos:
  workspace: .lytgos
  providers:
    - name: deepseek
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:}
    - name: kimi
      base-url: https://api.moonshot.cn/v1
      api-key: ${KIMI_API_KEY:}
```

要点：

- 密钥只从环境变量读取，配置文件只保留占位符；
- 缺少某个 Provider 的 Key 时，启动日志会给出警告并跳过该模型；
- 默认 Profile 使用 DeepSeek，因此 `chat` 前需要 `DEEPSEEK_API_KEY`；
- `provider list` 只读名称和地址，不会暴露 Key，也不会验证 Key。

临时设置密钥：

```powershell
$env:DEEPSEEK_API_KEY = "<你的密钥>"
lytgos chat
```

---

## 11. 退出码与错误处理

| 退出码 | 含义 |
|---:|---|
| 0 | 成功；查询类命令的空结果也属于成功 |
| 1 | 运行失败：文件不存在、Profile 非法、数据库读取失败、Agent 处理失败、端口冲突等 |
| 2 | 用法错误：未知命令、缺少参数、父命令未指定子命令 |

示例：

```powershell
lytgos profile show missing
$LASTEXITCODE   # 1
```

```powershell
lytgos profile
$LASTEXITCODE   # 2，显示子命令用法
```

CLI 错误输出统一以 `错误: ` 开头，不打印 Java 堆栈；需要看堆栈时查看 Spring 日志或重定向输出。

---

## 12. 常见工作流

### 12.1 第一次使用

```powershell
lytgos init
lytgos status
lytgos profile list
lytgos chat
lytgos session list
```

### 12.2 每天对话

```powershell
lytgos chat
```

同一目录、同一用户、同一 Profile 会继续原会话。

### 12.3 使用不同 Profile

```powershell
lytgos profile create weather
lytgos chat --profile weather
```

不同 Profile 会产生不同会话。

### 12.4 启动 HTTP 服务

```powershell
lytgos serve
```

另开终端：

```powershell
curl.exe http://localhost:8989/api/v1/health
curl.exe http://localhost:8989/api/v1/info
```

### 12.5 查看会话

```powershell
lytgos session list
```

### 12.6 每个项目使用独立工作区

```powershell
Set-Location D:\project-a
lytgos init
lytgos chat
```

`lytgos` 只使用当前目录下的 `.lytgos`，不会串到其他项目。

### 12.7 在一台机器上运行多个 Agent

当前 1.0 的“Agent”由 `profiles/*.yaml` 定义。有两种隔离方式。

**方式 A：同一工作区，多 Profile**

```powershell
lytgos init
lytgos profile create weather
lytgos profile create digest

lytgos chat --profile weather
lytgos chat --profile digest
```

- 会话按 `cli:<user>:<profile>` 隔离；
- 多个 Agent 共享同一个 `.lytgos/lytgos.db`、长期记忆和审计；
- 适合同一用户的多角色、多配置。

**方式 B：多个独立工作区**

```powershell
New-Item -ItemType Directory -Force D:\agents\weather | Out-Null
Set-Location D:\agents\weather
lytgos init
lytgos chat

New-Item -ItemType Directory -Force D:\agents\digest | Out-Null
Set-Location D:\agents\digest
lytgos init
lytgos chat
```

- 每个目录有独立的 `.lytgos/`、独立数据库和独立 Profile；
- 适合并行运行、不同 Provider/密钥/数据；
- 多个 `serve` 需要不同 `server.port`。

**并发注意**

- SQLite 单写者：同一个工作区不要同时运行多个 `chat`、`serve` 或 `gateway` 写进程；
- 需要并行测试时，优先使用方式 B；
- `serve` 默认端口 8989，多个服务必须显式换端口。

> 当前实现是 Profile YAML；`.lytgos/agents/<name>/AGENT.md` 目录模型属于后续能力，尚未在当前 CLI 中实现。

---

## 13. 故障排查

### 13.1 `lytgos` 命令找不到

- 第一次运行 JAR 后，启动器才会自动写入；检查是否已经以 `java -jar $JAR ...` 运行过至少一次；
- 新 PATH 只对新终端生效，重新打开 PowerShell；
- 检查启动器位置：
  - Windows：`%USERPROFILE%\.local\bin\lytgos.cmd`
  - macOS/Linux：`~/.local/bin/lytgos`
- PowerShell 检查：
  ```powershell
  Get-Command lytgos
  ```
- 如果自动安装被 `LYTGOS_NO_LAUNCHER_INSTALL=1` 禁用，可以去掉该变量后重试，或直接用 `java -jar $JAR ...`。

### 13.2 提示 JAR 不存在

```text
ERROR: JAR not found
```

先构建：

```powershell
.\mvnw.cmd -DskipTests package
```

### 13.3 `chat` 提示 AgentProfile 不存在

```text
错误: AgentProfile 不存在: ...\profiles\default.yaml
```

执行：

```powershell
lytgos init
lytgos profile list
```

或创建一个 Profile：

```powershell
lytgos profile create default
```

### 13.4 模型调用失败

检查：

```powershell
if ($env:DEEPSEEK_API_KEY) { "DEEPSEEK_API_KEY 已设置" } else { "DEEPSEEK_API_KEY 未设置" }
```

确认 Key 对当前终端可见，并且 Profile 使用的 provider 名称在 `application.yaml` 中存在。

### 13.5 端口被占用

`serve` 默认使用 8989。被占用时可以直接换端口：

```powershell
java -Dserver.port=9000 -jar $JAR serve
```

或停止占用端口的进程后重试。

### 13.6 数据库被锁

SQLite 同一时间只适合一个写进程。不要同时运行多个写同一个 `.lytgos/lytgos.db` 的 `chat`/`serve`。需要并行测试时，换一个目录或换一个工作区。

### 13.7 中文输出或日志乱码

如果 `lytgos` 自己的输出出现“宸ヤ綔鍖哄凡...”这类乱码，说明 Java 输出的 UTF-8 被终端按 GBK 解码了。当前 PowerShell 会话执行：

```powershell
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
```

然后重新运行命令即可。新开的 PowerShell 终端如果也乱码，可以把上面四行加入 `$PROFILE`。

如果终端字体本身不支持中文，设置 `LYTGOS_LANG=en` 让 CLI 文案回退英文。

如果只是 `Get-Content` 读日志文件乱码，指定 UTF-8：

```powershell
Get-Content .lytgos\logs\serve.out.log -Encoding UTF8 -Tail 50
```

推荐使用 Windows Terminal + PowerShell 7；Windows PowerShell 5.1 的传统控制台更容易出现编码问题。

### 13.8 `serve` 启动成功但没有 Agent 接口

当前版本只暴露 `health`、`info` 和 OpenAPI。Agent 会话/调用端点属于后续 Web Service 能力，暂未接入。

### 13.9 `status` 显示数据库未初始化

这是正常的：`init` 不创建数据库，第一次执行 `chat`、`serve` 或 `gateway` 时才会由 Flyway 创建。

---

## 14. 安全边界与 1.0 限制

- 1.0 **没有认证、多租户、SSO**；不要把 `serve` 端口直接暴露到公网。
- 密钥只通过环境变量注入，不要写进 Profile 或提交到 Git。
- `.lytgos/lytgos.db` 包含会话历史和审计记录，注意备份与访问权限。
- 自动启动器只修改当前用户的 PATH，不改系统 PATH，不需要管理员权限；可用 `LYTGOS_NO_LAUNCHER_INSTALL=1` 关闭。
- 当前不提供：内置 Tool、MCP、Sandbox、IM 渠道、AgentScheduler、Web 会话/Agent 端点、Agent 上传/生命周期 API。
- `gateway` 当前只是守护进程骨架，不会真正挂载渠道。
- 当前代码默认端口为 8989；其他历史文档如仍写 8080，以 `application.yaml` 和本手册为准。
- `/api/v1/info` 的 `profiles/providers` 计数仍是地基阶段的静态值；以 `lytgos status` 和启动日志为准。
- `profile list` 支持 `.yml`，但 `chat --profile` 当前按 `.yaml` 查找；统一使用 `profile create` 生成的 `.yaml`。
- CLI 文案语言支持中文/英文回退；对话内容可由模型按用户语言生成，不限于中文/英文。
- 可点击展开的思考过程、无 Java/Node 环境下的自动运行时下载，安排在扩展阶段；当前版本未包含。

---

## 15. 人工验收清单

- [ ] `lytgos --help` 退出码 0，能看到 5 个叶子命令和 4 个父命令。
- [ ] `lytgos init` 两次，第二次不覆盖已有文件。
- [ ] `lytgos status` 正确显示工作区、Profile、Provider 和会话状态。
- [ ] `lytgos profile create/list/show/delete` 正常，非法名称被拒绝。
- [ ] `lytgos provider list` 不输出任何 api-key。
- [ ] `lytgos tool list` 输出空说明。
- [ ] `lytgos session list` 空库时输出“暂无会话”，不创建数据库。
- [ ] `lytgos chat` 完成两轮对话，`/quit` 正常退出。
- [ ] 重启后再 `chat`，能引用上一轮内容。
- [ ] 多次 `chat` 后 `session list` 仍只有同一会话。
- [ ] `lytgos serve` 启动后 `/api/v1/health`、`/api/v1/info`、`/v3/api-docs` 返回 200。
- [ ] `lytgos gateway` 启动后保持运行，`Ctrl+C` 正常退出。
- [ ] 首次运行 JAR 后，`~/.local/bin/lytgos`（Windows 为 `.local\bin\lytgos.cmd`）自动生成；新终端可直接执行 `lytgos --version`。
- [ ] `LYTGOS_LANG=en` 时帮助和状态输出为英文；`LYTGOS_LANG=zh` 时为中文。
- [ ] 同一工作区创建两个 Profile，分别 `chat -p`，`session list` 显示两个不同会话。
- [ ] 两个独立工作区各自 `init` 后，数据库和会话互不影响。

---

## 16. 相关文档

- `specs/004-cli-session/quickstart.md`：CLI 与会话持久化的快速验证步骤。
- `specs/004-cli-session/contracts/cli.md`：命令行为与退出码契约。
- `specs/004-cli-session/contracts/session.md`：会话门面与持久化契约。
- `docs/TechnicalSolution.md`：CLI、Session、ReAct 的总体设计。
- `docs/ReleaseBaseline.md`：1.0 范围与命令口径。
