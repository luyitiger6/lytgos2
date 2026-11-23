# Contract: Command Line Interface

**Feature**: CLI and Session Persistence  
**Entry point**: `io.lytgos.cli.LytgosCli`（可执行 JAR 的 `mainClass`）

## 1. 命令树

```text
lytgos
├── init
├── status
├── chat        [--profile=<name>]
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

叶子命令共 12 个：`init`、`status`、`chat`、`serve`、`gateway`、`profile list`、`profile create`、`profile show`、`profile delete`、`provider list`、`tool list`、`session list`。父命令 4 个：`profile`、`provider`、`tool`、`session`。

## 2. 通用约定

| 项目 | 约定 |
|---|---|
| 命令名 | 小写、单词；与上面命令树逐字一致 |
| 帮助 | 根命令与每个子命令支持 `--help`，使用 Picocli 标准帮助，退出码 0 |
| 版本 | 根命令支持 `--version`，输出 `LytgOS2 <version>` |
| 工作区 | 默认当前目录下 `.lytgos`，不提供 `--workspace` 参数 |
| 成功输出 | 写入 Picocli `getOut()` |
| 错误输出 | 写入 Picocli `getErr()`，格式 `错误: <可读说明>`，不打印堆栈 |
| 退出码 0 | 命令成功；查询命令的空结果也返回 0 |
| 退出码 1 | 运行失败：IO 错误、文件缺失、Profile 非法、数据库读取失败、Agent 处理异常 |
| 退出码 2 | 用法错误：未知命令、缺少参数、父命令未指定子命令（Picocli 默认） |
| 标准流 | 业务代码不直接访问 `System.out/err/in`；输出走 Picocli 流，输入用 `FileDescriptor.in` |
| 凭证 | 任何命令不得输出 api-key；`provider list` 的视图类型没有凭证字段 |

## 3. 轻命令与重命令

| 类别 | 命令 | 是否启动 Spring |
|---|---|---|
| 轻命令 | `init`、`status`、`profile *`、`provider list`、`tool list`、`session list` | 否 |
| 重命令 | `chat`、`serve`、`gateway` | 是 |

判断标准：命令是否调用模型或运行 Agent 引擎。轻命令直接读写文件或 SQLite；重命令通过反射加载 `io.lytgos.boot.LytgosApplication` 并启动 Spring。

## 4. 命令契约

### 4.1 `lytgos init`

**行为**

1. 解析工作区根为当前目录下 `.lytgos`。
2. 幂等创建目录：`agents`、`skills`、`memory`、`sessions`、`logs`、`profiles`。
3. 创建占位文件（不存在时）：`AGENTS.md`、`SOUL.md`、`USER.md`、`MEMORY.md`、`mcp_servers.yaml`。
4. 创建 `profiles/default.yaml`（不存在时），内容为 data-model.md §5.2 的模板。
5. 已存在文件保持原内容，不覆盖、不报错。
6. 输出创建与跳过的条目摘要。

**错误**：目录或文件创建失败 → 退出码 1，消息包含失败路径。

### 4.2 `lytgos status`

**行为**

输出以下只读摘要：

```text
工作区: <absolute path>
数据库: 已初始化 | 未初始化
AgentProfile: N
Provider: N
会话: N | 未初始化
```

- 工作区不存在：仍输出摘要，数据库与计数显示未初始化，退出码 0。
- `Provider` 数量来自 classpath `application.yaml` 的 `lytgos.providers`。
- `会话` 数量直读 SQLite；数据库或 `sessions` 表不存在时显示 `未初始化`。
- 不修改任何文件或表。

### 4.3 `lytgos chat [--profile=<name>]`

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `--profile` / `-p` | 否 | `default` | 使用的 AgentProfile 名称 |

**行为**

1. 校验 Profile 名称合法且 `profiles/<name>.yaml` 存在；不满足则退出码 1，不启动 Spring。
2. 启动 Spring（`WebApplicationType.NONE`），获取 `AgentService` 与 `SessionManager`。
3. 以 `channel="cli"`、当前系统用户名（`user.name`，缺失时 `local`）、Profile 名称调用 `getOrCreate`。
4. 循环：输出提示符 `> `；读取一行。
   - 输入流结束（EOF）→ 退出循环。
   - 去首尾空白后为空 → 继续下一轮。
   - 等于 `/quit` → 退出循环。
   - 其他内容 → `agentService.process(session, line)`，把返回文本打印到 `getOut()`。
5. Agent 处理抛异常：向 `getErr()` 输出 `错误: <message>`，继续循环；进程最终退出码为 1。
6. 正常 EOF 或 `/quit`：关闭 Spring 上下文，退出码 0（若本轮未发生错误）。

`CliChannel` 只负责读取、转交、打印；不包含推理、模型调用、工具执行或会话标识拼接。

### 4.4 `lytgos serve`

启动 Spring（`WebApplicationType.SERVLET`），保持运行直到进程收到终止信号。共享 `.lytgos` 下的 Profile 与 Session 存储。退出码 0 表示正常关闭。

### 4.5 `lytgos gateway`

启动 Spring（`WebApplicationType.NONE`），注册 JVM 关闭钩子，然后阻塞等待直到进程终止。本功能只提供守护进程骨架，不挂载 IM 渠道。退出码 0 表示正常关闭。

### 4.6 `lytgos profile list`

列出 `profiles/*.yaml` 与 `*.yml` 的 Profile 名称（文件名去扩展名），按名称排序。无 Profile 或目录不存在时输出 `暂无 AgentProfile`，退出码 0。

### 4.7 `lytgos profile create <name>`

| 参数 | 必填 | 约束 |
|---|---|---|
| `<name>` | 是 | 匹配 `[A-Za-z0-9_-]+`，不得包含路径分隔符或 `..` |

**行为**：在 `profiles/<name>.yaml` 写入 data-model.md §5.2 的模板，`name` 替换为目标名称。文件已存在 → 退出码 1，不覆盖。

### 4.8 `lytgos profile show <name>`

校验名称后原样打印 `profiles/<name>.yaml` 内容。文件不存在 → 退出码 1。

### 4.9 `lytgos profile delete <name>`

校验名称后删除 `profiles/<name>.yaml`。文件不存在 → 退出码 1。无交互确认，便于脚本使用。

### 4.10 `lytgos provider list`

从 classpath `application.yaml` 读取 `lytgos.providers`，输出两列：

```text
NAME       BASE URL
deepseek   https://api.deepseek.com
kimi       https://api.moonshot.cn/v1
```

- 只输出 `name` 与 `base-url`；不读取、不解析、不输出 `api-key`。
- 凭证缺失不影响列表。
- 无 provider 时输出 `暂无 Provider 配置`，退出码 0。
- 配置读取失败 → 退出码 1。

### 4.11 `lytgos tool list`

当前 Tool 能力尚未接入，固定输出 `暂无已注册工具（Tool 能力尚未接入）`，退出码 0。不启动 Spring。

### 4.12 `lytgos session list`

直读 `.lytgos/lytgos.db` 的 `sessions` 表，按 `last_active_at` 倒序输出：

```text
SESSION ID              PROFILE    CHANNEL   USER    STATUS   LAST ACTIVE
cli:wang:default         default    cli       wang    ACTIVE   2026-11-23T10:00:00Z
```

- 数据库不存在、`sessions` 表不存在或表为空：输出 `暂无会话`，退出码 0；不创建数据库或表。
- SQLite 读取失败（文件损坏等）：退出码 1，消息包含原因。
- 只读连接使用 `jdbc:sqlite:<path>?mode=ro` 或等价的只读设置，不写入。

## 5. 重命令启动契约

`SpringRuntime` 是 CLI 内唯一启动 Spring 的组件：

```java
ApplicationContext start(WebApplicationType webType);

void close();
```

- 通过反射加载 `io.lytgos.boot.LytgosApplication`：`Class.forName("io.lytgos.boot.LytgosApplication")`。
- 使用 `SpringApplicationBuilder(loadedClass).web(webType).run()`。
- 反射失败（类或方法缺失、启动异常）→ 转为 `IllegalStateException("无法启动运行时: ...")`，由命令输出错误并返回退出码 1。
- CLI 模块不编译依赖 `lytgos-boot`；boot fat JAR 将 CLI 与 boot 打包在一起，运行时类一定存在。
- 启动时 `PersistenceConfiguration` 的 `@EntityScan("io.lytgos")` 与 `@EnableJpaRepositories("io.lytgos")` 生效，日志出现非零仓库数量。

## 6. `CliChannel` 契约

```java
public final class CliChannel {

  public CliChannel(
      AgentService agentService,
      SessionManager sessionManager,
      String user,
      String profileName,
      BufferedReader input,
      PrintWriter output,
      PrintWriter errorOutput);

  public int run();
}
```

| 项目 | 约定 |
|---|---|
| 构造器 | 所有参数非空；`input` 由调用方用 `FileDescriptor.in` 包装 |
| `run()` 返回 | 无错误且正常退出返回 0；发生 Agent 错误返回 1 |
| 会话 | 只在 `run()` 内调用一次 `getOrCreate("cli", user, profileName)` |
| 退出 | `/quit`（去空白后）或 EOF |
| 输出 | 提示符与回复写 `output`；错误写 `errorOutput` |
| 依赖 | 只依赖 core 的 `AgentService`/`SessionManager` 与 JDK IO；不依赖 Spring |

## 7. 验收映射

| 验收点 | 验证方式 |
|---|---|
| 12 个叶子命令存在 | `LytgosCliTest.allTwelveLeafCommandsAreRegistered` |
| 根帮助成功 | `LytgosCliTest.rootHelpExitsSuccessfully` |
| 每个叶子命令帮助成功 | `LytgosCliTest.everyLeafCommandHelpExitsSuccessfully` |
| 轻重分流 | 人工：轻命令无 Spring 日志；`chat`/`serve` 日志含 `Starting LytgosCli` 与非零仓库数 |
| 多轮对话与 `/quit` | 人工：真实模型两轮 + `/quit`；自动化可选 `CliChannelTest` |
| Profile 管理 | 人工：`profile create/list/show/delete` 冒烟 |
| Provider 无凭证 | 人工：`provider list` 输出逐行检查 |
| 会话列表空态 | 人工：未初始化工作区执行 `session list`，输出 `暂无会话` |
