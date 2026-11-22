# Quickstart: Agent Provider（第 16 节）

## 1. 前置条件

- JDK 21，`mvn -v` 显示 Java 21
- 仓库根目录可执行 `./mvnw`（Windows 用 `mvnw.cmd`）
- 单元测试不需要真实模型账号
- 集成冒烟需要 `DEEPSEEK_API_KEY`；`KIMI_API_KEY` 可选
- 工作区目录默认为 `.lytgos/`，Profile 放在 `.lytgos/profiles/`

### 1.1 实际依赖版本（实现时已核实）

- `org.springframework.ai:spring-ai-openai:1.1.2`
- `org.flywaydb:flyway-core:11.7.2`：SQLite 支持已内置于 `flyway-core`，Maven Central 没有 `flyway-database-sqlite` 制品，不要引入
- `org.xerial:sqlite-jdbc:3.49.1.0`
- `org.hibernate.orm:hibernate-community-dialects:6.6.53.Final`

## 2. 准备 Profile 与全局 Provider

在 `.lytgos/profiles/ops-agent.yaml` 放入一个示例 Profile（字段说明见 [contracts/profile-config.md](./contracts/profile-config.md)）：

```yaml
name: ops-agent
description: 运维助手
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
tools: []
settings:
  max_iterations: 10
  max_history_turns: 20
```

`application.yaml` 的 `lytgos.providers` 至少包含：

```yaml
lytgos:
  providers:
    - name: deepseek
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:}
```

## 3. 单元测试

```bash
./mvnw -B -ntp test
```

模块级快速回归：

```bash
./mvnw -B -ntp -pl lytgos-core test
./mvnw -B -ntp -pl lytgos-provider -am test
./mvnw -B -ntp -pl lytgos-storage -am test
```

预期：

- `ProfileLoaderTest`：合法 Profile 全字段解析、未知 provider 报错、坏文件不阻断其他 Profile、环境变量占位符解析。
- `ProviderServiceTest`：按名路由不串台、未知名报错、成功/失败都写审计、工具请求关闭自动执行。
- `ToolSchemaAdapterTest`：工具 name/description/inputSchema 逐字段对齐，执行入口被拒绝。
- `LlmCallRepositoryTest`：Flyway 建出的 `llm_calls` 能存能读，`success`/`error_message` 列存在。
- `ProviderSmokeIT` 默认被 Surefire 的 `integration` 组排除。

## 4. 全量质量门禁

```bash
./mvnw -B -ntp clean verify
```

预期：

- 所有单元测试通过；
- 阿里巴巴 Java 开发规约（P3C）、SpotBugs、FindSecBugs、PMD、Checkstyle、ArchUnit 全部通过；
- 不运行需要真实模型账号的集成冒烟。

P3C 阻断门禁单独执行：

```bash
./mvnw -B -ntp -Pp3c verify
```

预期 `BUILD SUCCESS`。

## 5. 手动集成冒烟

```bash
DEEPSEEK_API_KEY=... ./mvnw -B -ntp -pl lytgos-provider -Pintegration -Dtest=ProviderSmokeIT test
```

Windows PowerShell：

```powershell
$env:DEEPSEEK_API_KEY = "..."
.\mvnw.cmd -B -ntp -pl lytgos-provider -Pintegration "-Dtest=ProviderSmokeIT" test
```

预期：

- 测试真实调用一次 DeepSeek，返回非空响应；
- 记录一条 `success=true` 的审计；
- 缺少 key 时测试通过 `Assumptions` 跳过，不失败。

## 6. 人工核对项

1. **依赖可解析**：
   ```bash
   ./mvnw -B -ntp -pl lytgos-provider dependency:tree -Dincludes=org.springframework.ai
   ```
   预期包含 `org.springframework.ai:spring-ai-openai:1.1.2`。

2. **无明文密钥**：
   ```bash
   grep -r "sk-" --exclude-dir=target --exclude-dir=.git .
   ```
   预期无命中。

3. **审计落库（可选）**：
   运行一次带真实存储的调用后，可用 SQLite 客户端查询：
   ```sql
   select provider, model, success, error_message, duration_ms
   from llm_calls order by id desc limit 5;
   ```
   预期最近一次真实调用 `success=1`；失败场景应有 `success=0` 且 `error_message` 非空。

4. **Profile 校验**：
   将 Profile 的 `provider.name` 改成不存在的名称，预期加载阶段报出清晰错误，其他合法 Profile 不受影响。
