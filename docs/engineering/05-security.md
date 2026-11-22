# 安全基线

## 1. 分层检查

| 层 | 工具 | 运行位置 | 作用 |
|---|---|---|---|
| SAST（字节码） | SpotBugs + FindSecBugs | `mvn verify` | SQL 注入、XSS、路径穿越、弱加密、XXE、不安全反序列化 |
| SAST（源码） | PMD 7 | `mvn verify` | 缺陷、复杂度、危险模式 |
| SCA | OWASP Dependency-Check | CI `security` job | 第三方依赖 CVE |
| SBOM | CycloneDX | CI `sbom` job | 交付物物料清单 |
| 密钥扫描 | gitleaks | CI + pre-commit | 明文密钥、token、私钥 |
| 依赖审查 | dependency-review-action | PR | 新增依赖的许可证与漏洞 |

CodeQL / Semgrep 作为可选增强：公开仓库优先 CodeQL，私有仓库可用 Semgrep OSS；不要同时引入多套重复的 SAST。

## 2. 密钥与敏感信息

- 所有密钥使用 `${ENV_VAR}` 占位符，禁止写入 `AGENT.md`、`application.yaml` 或测试资源。
- DashScope/Qwen 使用 `DASHSCOPE_API_KEY`；SAA 默认 `spring.ai.dashscope.enabled=false`，不要为了启动方便写默认 key。
- 日志不得输出 API key、Authorization、webhook URL 的 `key=` 参数。
- `GET /api/v1/info` 不得返回工作区绝对路径、密钥或审计内容。
- 测试用密钥必须是明显的假值，并放在测试资源中。

## 3. 运行时边界

- 1.0 无认证/RBAC/SSO，按可信内网部署；这是基线决策，不要在代码里静默加认证或收紧 CORS。
- Actuator 只暴露 `health` / `info`，不暴露 `env`、`heapdump`、`threaddump` 等高危端点。
- 不做 Prometheus 指标暴露；不要添加 `micrometer-registry-prometheus`。
- Sandbox 接入后必须覆盖：`../` 路径穿越、Shell 可执行文件白名单、HTTP 域名白名单。
- Provider/MCP 的网络调用必须设置超时；不要把超时异常吞掉。

## 4. 供应链

- 使用 Maven Wrapper，CI 和本地同一个 Maven 版本。
- 外部依赖禁止 SNAPSHOT；内部模块使用 `${project.version}`。
- 新增依赖前检查：维护活跃度、许可证、已知 CVE、是否与 Jackson 2 / Boot 3.5 兼容。
- Dependency-Check 需要 `NVD_API_KEY`；无 key 时只跑 SBOM，不阻塞主构建。
- 发版产物附带 `target/bom.json`。

## 5. 安全扫描的边界

静态扫描不能替代运行时测试。以下场景必须有测试或手工验收：

- Session 不存在 / 已归档 / 超时。
- 非法 JSON、超长消息、非法 cron。
- Provider 不可用、MCP 不可用。
- 路径穿越、非白名单命令、非白名单域名（Sandbox 实现后）。
