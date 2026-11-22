---
name: lytgos-quality
description: >-
  对 LytgOS2 做持续质量审计与发版门禁：运行 mvn verify、分类修复 Spotless/
  Checkstyle/PMD/SpotBugs/ArchUnit/测试失败、执行 gitleaks/Dependency-Check/SBOM、
  核对模块依赖矩阵与 API 契约，并输出残留风险。用于“代码检查/质量门禁/安全扫描/
  发版前检查/审计某个模块”。
---

# lytgos-quality：持续质量审计

本 skill 负责“检查、定位、修复、复验、报告”，不负责第一次搭骨架（那是 `lytgos-init`）。

## 权威输入

- `docs/engineering/04-quality.md`
- `docs/engineering/05-security.md`
- `docs/engineering/07-module-standards.md`
- `docs/engineering/03-api.md`
- `specs/001-lytgos-runtime-kernel/contracts/`

## 工作流

### 1. 跑主门禁

```bash
./mvnw -B -ntp verify
```

按失败类别定位：

| 失败 | 位置 | 处理 |
|---|---|---|
| Spotless | `target/spotless-*` / 控制台 | `./mvnw spotless:apply`，不要手改 |
| Checkstyle | `target/checkstyle-result.xml` | 改命名/import/行宽；不要加全局 suppression |
| PMD | `target/pmd.xml` | 修缺陷/复杂度；误报加窄范围 suppression |
| SpotBugs | `target/spotbugsXml.xml` | 修真实缺陷；DTO 不可变集合才可窄范围排除 |
| ArchUnit | surefire 报告 | 改依赖方向，不要放宽规则 |
| 测试 | `target/surefire-reports/` | 修代码或补测试，不跳过 |

### 2. 跑 P3C 报告（可选）

```bash
./mvnw -B -ntp -Pp3c verify
```

P3C 是补充报告，不阻断合并。按“Google 优先”处理冲突；只汇总高价值问题，不把 P3C 噪声当成主门禁失败。

### 3. 跑安全门禁

```bash
# 有 NVD_API_KEY 时
NVD_API_KEY=... ./mvnw -B -ntp -Psecurity verify

# 只生成 SBOM
./mvnw -B -ntp -Psecurity -Ddependency-check.skip=true verify -DskipTests
```

另外确认：

- gitleaks 无命中；命中则轮换密钥，不能只删提交。
- 新增依赖已检查许可证与 CVE。
- 没有把密钥、token、webhook `key=` 写进日志或配置。
- Actuator 只暴露 health/info；没有 Prometheus registry。

### 4. 核对模块与契约

- 对照 `docs/engineering/07-module-standards.md` 检查依赖方向。
- 新增/修改 REST 字段时，对照 `contracts/rest-api.md`，确认信封、状态码、示例同步。
- 新增 CLI 命令时，对照 `contracts/cli.md`，确认退出码、stdout/stderr、`--json` 行为。
- 新增业务异常时，确认映射到既有 400/404/409/500/503/504，不新增无契约状态码。

### 5. 输出报告

用中文输出，至少包含：

```text
命令：
结果：通过 / 失败（失败项按类别列出）
修复：改了哪些文件、为什么
残留风险：需要人工确认或后续处理的事项
未执行项：例如没有 NVD_API_KEY、没有跑压测
```

不得声称未实际运行的测试通过。没有跑安全扫描就明确写“未执行”。

## 修复原则

1. 先修真实问题，再加 suppression；suppression 必须窄范围并注释原因。
2. 不通过 `-DskipTests`、`-Dpmd.skip`、`-Dspotbugs.skip` 让主分支变绿。
3. 不为了让扫描通过而改变业务语义；需要改契约时先改文档。
4. 新规则先在报告模式观察，再升级为阻断。
5. 安全扫描发现高危漏洞时，优先升级依赖；不能升级时记录影响范围和缓解措施。

## 不做什么

- 不添加 Prometheus、Grafana、认证、RBAC、SSE/WebSocket。
- 不在未修订 `ReleaseBaseline`/ADR 的情况下移除 SAA 或切换 Boot 4。
- 不把 P3C 硬门禁和 PMD 7 放在同一次 Maven 调用。
- 不删除测试或降低 ArchUnit 规则来换取绿灯。
- 不在没有实际运行的情况下写“测试通过”。
