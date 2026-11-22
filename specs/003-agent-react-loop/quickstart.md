# Quickstart: ReAct Agent Loop

## 1. 前置条件

- JDK 21，`mvn -v` 显示 Java 21。
- 仓库根目录执行 `.\mvnw.cmd`（Windows）或 `./mvnw`（Linux/macOS）。
- 单元测试不需要真实模型账号；真实 Provider 冒烟需要 `DEEPSEEK_API_KEY`。
- 工作区默认 `.lytgos/`；AgentProfile 放在 `.lytgos/profiles/`。

实现时核实的依赖版本：`spring-ai-openai:1.1.2`、`flyway-core:11.7.2`（SQLite 支持内置，无 `flyway-database-sqlite` 制品）、`sqlite-jdbc:3.49.1.0`、`hibernate-community-dialects:6.6.53.Final`。

## 2. 准备 AgentProfile、Bootstrap 与 Skill（可选，人工观察用）

`.lytgos/profiles/ops-agent.yaml`：

```yaml
name: ops-agent
description: 运维助手
identity:
  agent_name: 运维助手
  prompt: 你是一个谨慎的运维助手，先查证再回答。
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0
tools:
  - echo
bootstrap:
  - SOUL.md
skills:
  - daily-report
settings:
  max_iterations: 10
  max_history_turns: 20
```

`.lytgos/SOUL.md`：

```markdown
# 角色
你负责回答运维相关问题。
```

`.lytgos/skills/daily-report/SKILL.md`：

```markdown
---
name: daily-report
description: 生成每日运维简报
---

# 每日简报
```

第 17 节启动应用只会加载 AgentProfile；`echo` 工具在第 20 节 Tool 课程才注册，当前工具集合为空。

## 3. 单元测试

```bash
./mvnw -B -ntp test
```

预期：

- `ReActLoopTest`：无工具调用一轮收尾；工具调用执行并回填；转满最大轮数恰好调用 Provider 10 次；每轮响应和工具结果累积进 Session。
- `PromptBuilderTest`：system prompt 顺序正确；历史超 N 轮被截断；末尾包含当前日期时间。
- `ToolExecutorTest`：成功/失败都写审计；工具不存在和异常路径不吞异常。
- `AgentServiceTest`：处理期间 AgentProfileContext 可取到；异常时 finally 清理；保存钩子被调用。
- `ContextLoaderTest`：修改文件后下一次读取立即生效；Skill 缺失报错；Bootstrap 缺失 WARN。
- `ToolInvocationRepositoryTest`：Flyway V2 建出的 `tool_invocations` 可存可读，`success`/`error_message` 列存在。

实现完成时全量套件共 67 个单元测试（含第 16 节回归）；默认构建不运行真实模型冒烟。

模块级快速回归：

```bash
./mvnw -B -ntp -pl lytgos-core test
./mvnw -B -ntp -pl lytgos-storage -am test
```

## 4. 全量质量门禁

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Pp3c verify
```

预期均为 `BUILD SUCCESS`；`integration` 组默认排除。

## 5. 真实模型冒烟

第 16 节已有的 Provider 冒烟仍可运行：

```powershell
.\mvnw.cmd -B -ntp -pl lytgos-provider -am -Pintegration "-Dtest=ProviderSmokeIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期 `Tests run: 1, Failures: 0, Errors: 0`。

## 6. 工具审计落库检查

`ToolInvocationRepositoryTest` 会在系统临时目录创建 SQLite 并执行 Flyway V1+V2。找到测试库：

```powershell
Get-ChildItem $env:TEMP -Directory -Filter "lytgos-tool-invocation-test-*" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 3 FullName, LastWriteTime
```

用 SQLite 客户端查询：

```sql
SELECT id, session_id, tool_name, input_json, success,
       error_message, duration_ms, created_at
FROM tool_invocations
ORDER BY id DESC
LIMIT 10;
```

## 7. 人工项与课程阶段限制

- 课件 Demo 一需要真实模型调用 `http_get`。`http_get` 属于第 20 节内置 Tool；第 17 节完成后先用工具替身和 harness 验证 ReAct，等第 20 节交付工具后再跑真实多轮 Demo。
- 本轮真实模型冒烟（`ProviderSmokeIT`）验证的是第 16 节 Provider 单次调用，不包含工具执行。
- 确认没有使用 Spring AI Agent 封装：代码审查 `lytgos-core` 的 `ReActLoop`，模型调用只通过 `LlmGateway`，工具只通过 `ToolExecutor`。
