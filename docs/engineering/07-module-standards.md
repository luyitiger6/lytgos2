# 模块规范

## 1. 依赖矩阵

| 模块 | 允许依赖 | 禁止依赖 | 最低测试要求 |
|---|---|---|---|
| `lytgos-core` | JDK、必要基础库 | provider/storage/web/cli/tool/boot | 纯单元测试 + 架构测试 |
| `lytgos-provider` | core、Spring AI、Spring AI Alibaba | storage/web/cli/boot | Provider 映射 + WireMock 契约测试 |
| `lytgos-memory` | core | SQLite/web/Spring AI | MarkdownMemoryStore 单元测试 |
| `lytgos-tool` | core | storage/web/provider | ToolRegistry + Sandbox 安全测试 |
| `lytgos-storage` | core、JPA/SQLite/Flyway | web/provider/tool | Flyway 迁移 + repository 集成测试 |
| `lytgos-web` | core、Spring MVC、springdoc | storage/provider 实现 | MockMvc/HTTP 契约测试 |
| `lytgos-cli` | core、Picocli | storage/provider 实现 | 命令解析与退出码测试 |
| `lytgos-channel-cli` | core | storage/web | REPL 输入输出测试 |
| `lytgos-boot` | 全部模块 | 被任何模块反向依赖 | context smoke + 至少一个 E2E |

`lytgos-boot` 是唯一组装根；其他模块通过 core 端口交互，不直接 new 实现类。

## 2. 机器执行

依赖方向由 `lytgos-boot/src/test/java/io/lytgos/boot/architecture/ModuleArchitectureTest.java` 强制：

- `core` 不依赖任何实现模块。
- `web` 不直接依赖 `storage` / `provider`。
- `io.lytgos.(*)` 切片无环。
- 禁止 `System.out` / `System.err` / `System.in`。

新增模块或新增跨模块依赖时，先改本文和 ArchUnit 规则，再改代码。

## 3. 每个模块的 Definition of Done

- [ ] `pom.xml` 只包含允许的依赖。
- [ ] 至少一个可运行测试（不只是编译）。
- [ ] 新增公共类型有 Javadoc；包级说明可选但推荐 `package-info.java`。
- [ ] 错误路径有测试或明确说明。
- [ ] 日志不包含密钥、绝对路径、完整请求头。
- [ ] `./mvnw verify` 通过。
- [ ] 涉及 API/CLI 契约时，同步 `specs/001-lytgos-runtime-kernel/contracts/`。

## 4. 命名与包结构

- Java 类名前缀 `Lytgos*` 只用于入口/产品级类型；普通领域类型用职责命名。
- 包名：`io.lytgos.<module>.<layer>`，层名限定为 `api`、`engine`、`port`、`model`、`config`、`store`、`entity`、`repository`、`sandbox`、`mcp`、`builtin`。
- 不新增 `util` / `common` 大杂烩包；共享契约放 `lytgos-core` 的 `port` 或 `model`。
- 测试包与被测包同名，测试类以 `Test` 结尾。
