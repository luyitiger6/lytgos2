# Quickstart: CLI and Session Persistence

## 1. 前置条件

- JDK 21（`mvn -v` 显示 Java 21）。
- 仓库根目录 `D:\lyagent\lytgos2`。
- 真实对话需要一个可用的 provider 密钥（默认 DeepSeek）：`DEEPSEEK_API_KEY` 已存在于当前环境变量。
- Windows 使用 `mvnw.cmd`，Git Bash/macOS/Linux 使用 `./mvnw`。

## 2. 构建与自动化验证

```bash
# 全量质量门禁（推荐；包含 Spotless/Checkstyle/PMD/SpotBugs/ArchUnit/JaCoCo）
./mvnw -B -ntp clean verify

# P3C 阻断门禁
./mvnw -B -ntp -Pp3c verify

# 只跑会话层验收
./mvnw -B -ntp -pl lytgos-storage -am test -Dtest=SessionManagerTest,SessionRepositoryTest

# 只跑 CLI 命令注册与帮助验收
./mvnw -B -ntp -pl lytgos-cli -am test -Dtest=LytgosCliTest
```

预期：

- 全部命令退出码 0。
- `SessionManagerTest`、`SessionRepositoryTest`、`LytgosCliTest` 全绿。
- 既有 67 个测试保持全绿。

## 3. 生成可执行 JAR

```bash
./mvnw -B -ntp -DskipTests package
```

产物：`lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar`。JAR 的入口已是 `io.lytgos.cli.LytgosCli`。

下文用 `JAR` 表示该路径；在空目录中执行，避免污染仓库工作区：

```bash
mkdir -p /tmp/lytgos-demo && cd /tmp/lytgos-demo
java -jar /path/to/lytgos2/lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar --help
```

预期：显示 `lytgos` 用法、12 个叶子命令和 4 个父命令，退出码 0。

首次从 JAR 运行任意命令时，会尝试把 `lytgos` 启动器安装到用户级 `~/.local/bin`（Windows 为 `%USERPROFILE%\.local\bin\lytgos.cmd`）并加入用户 PATH；新终端可直接使用 `lytgos ...`。设置 `LYTGOS_NO_LAUNCHER_INSTALL=1` 可关闭自动安装。

## 4. 轻命令冒烟

```bash
# 初始化工作区
java -jar "$JAR" init
```

预期输出创建/跳过摘要；目录结构：

```text
.lytgos/
├── agents/
├── skills/
├── memory/
├── sessions/
├── logs/
├── profiles/default.yaml
├── AGENTS.md
├── SOUL.md
├── USER.md
├── MEMORY.md
└── mcp_servers.yaml
```

再次执行 `init`：所有条目显示“已存在”，已有文件内容不变。

```bash
# 状态与配置查询（均应秒回，不出现 Spring 启动日志）
java -jar "$JAR" status
java -jar "$JAR" profile list
java -jar "$JAR" profile create weather
java -jar "$JAR" profile show weather
java -jar "$JAR" profile delete weather
java -jar "$JAR" provider list
java -jar "$JAR" tool list
java -jar "$JAR" session list
```

预期：

- `status`：工作区绝对路径、数据库未初始化、`AgentProfile: 1`、`Provider: 2`、会话未初始化。
- `profile create weather` 生成 `profiles/weather.yaml`；重复创建报错且不覆盖。
- `profile show weather` 原样输出 YAML；删除后文件消失。
- `provider list` 只显示 `deepseek`、`kimi` 及 base URL，**不出现任何 key**。
- `tool list`：`暂无已注册工具（Tool 能力尚未接入）`。
- `session list`：`暂无会话`，且不创建 `lytgos.db`。

### 边界检查

```bash
java -jar "$JAR" profile show ../secret   # 退出码 1，名称非法
java -jar "$JAR" profile show missing     # 退出码 1，文件不存在
java -jar "$JAR" profile                 # 退出码 2，显示子命令用法
```

## 5. 重命令：终端对话

确保当前 shell 能读到 `DEEPSEEK_API_KEY`，然后：

```bash
java -jar "$JAR" chat
```

预期启动日志包含：

```text
Starting LytgosCli
Finished Spring Data repository scanning ... found N JPA repository interfaces
```

`N` 必须大于 0（`llm`、`tool`、`session` 等仓库都被扫描）。

进入交互后：

```text
> 你好，介绍一下你自己
<Agent 回复>
> 记住：我的偏好是简洁回答
<Agent 回复>
> /quit
```

退出码 0。再次执行 `chat`，进入同一 `cli:<用户名>:default` 会话，追问“我刚才说了什么偏好？”，应能引用上一轮内容。这说明历史已从 SQLite 恢复。

排查会话：

```bash
java -jar "$JAR" session list
```

预期出现 `cli:<用户名>:default` 行，状态 `ACTIVE`，最后活跃时间为刚才时间。

如本机有 `sqlite3`：

```bash
sqlite3 .lytgos/lytgos.db \
  "select session_id, profile_name, channel, user_id, status, length(messages_json) from sessions;"
```

预期一行，`messages_json` 长度大于 2（不是空数组）。

### 指定 Profile

```bash
java -jar "$JAR" chat --profile weather
```

若 `profiles/weather.yaml` 不存在，命令在启动 Spring 前报错并返回退出码 1。

### 错误输入

- 直接按 Ctrl+C 或关闭输入流：命令正常结束，不打印堆栈。
- 模型调用失败：输出 `错误: ...`，循环继续；可继续输入或 `/quit`，最终退出码 1。

## 6. 重命令：serve 与 gateway

```bash
# 终端 1
java -jar "$JAR" serve
```

默认端口是 8989。直接启动即可：

```bash
java -jar "$JAR" serve
```

预期 Spring 以 SERVLET 模式启动，8989 端口可用：

```bash
curl http://localhost:8989/api/v1/health
curl http://localhost:8989/api/v1/info
```

若 8989 也被占用，可用系统属性临时换端口：

```bash
java -Dserver.port=18080 -jar "$JAR" serve
```

Ctrl+C 正常关闭。

```bash
# 终端 2
java -jar "$JAR" gateway
```

预期 Spring 以 NONE 模式启动并保持运行；Ctrl+C 正常关闭。本功能不挂载 IM 渠道。

## 7. 重启恢复验证

1. 完成第 5 节两轮对话并 `/quit`。
2. 确认进程已退出。
3. 再次 `java -jar "$JAR" chat`，提问与上一轮相关的问题。
4. 预期模型能引用上一轮内容；`session list` 仍只有同一行。

## 8. 人工验收清单

- [ ] 12 个叶子命令 `--help` 均退出码 0。
- [ ] 轻命令秒回，输出中无 `Starting LytgosCli`。
- [ ] `chat`/`serve`/`gateway` 日志含 `Starting LytgosCli` 与 `found N JPA repository interfaces`（N > 0）。
- [ ] `chat` 完成两轮对话，`/quit` 正常退出。
- [ ] 重启后同一三元组历史仍在，`session list` 不产生重复行。
- [ ] `init` 第二次执行不覆盖已有文件。
- [ ] `provider list` 输出不含任何凭证。
- [ ] `session list`/`tool list` 空数据时输出可读说明，无堆栈。
- [ ] `serve` 健康端点可访问；`gateway` 可启动并正常终止。
- [ ] 三种模式共享同一 `.lytgos/profiles` 与 `.lytgos/lytgos.db`。

## 9. 清理

```bash
cd ..
rm -rf /tmp/lytgos-demo
```

Windows PowerShell：

```powershell
Remove-Item -Recurse -Force C:\temp\lytgos-demo
```
