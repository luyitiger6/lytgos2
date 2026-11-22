# 构建基线

## 1. 模块与依赖方向

九个模块固定不变，依赖方向由 `ModuleArchitectureTest` 和 Maven Enforcer 共同约束。

```text
lytgos-boot        （唯一组装根，依赖全部模块）
  ├── lytgos-web
  ├── lytgos-cli
  ├── lytgos-channel-cli
  ├── lytgos-provider
  ├── lytgos-memory
  ├── lytgos-tool
  └── lytgos-storage
        └── 以上实现模块都只依赖 lytgos-core
```

`lytgos-core` 不得依赖任何实现模块；`lytgos-web` 不得直接依赖 `lytgos-storage` 或 `lytgos-provider`。跨模块调用走 core 的端口。

## 2. 常用命令

```bash
# 首次构建
./mvnw clean verify

# 只格式化（写回文件）
./mvnw spotless:apply

# 只跑格式检查
./mvnw spotless:check

# 跑安全扫描（需要 NVD_API_KEY）
NVD_API_KEY=... ./mvnw -Psecurity verify

# P3C 阻断门禁
./mvnw -Pp3c verify

# 跳过测试做快速编译
./mvnw -DskipTests compile
```

Windows 使用 `mvnw.cmd` 替代 `./mvnw`。

## 3. 质量门禁绑定

| 阶段 | 插件 | 结果 |
|---|---|---|
| `validate` | Maven Enforcer | JDK/Maven 版本、插件版本、重复依赖 |
| `validate` | Checkstyle | 命名、import、行宽、空块 |
| `verify` | Spotless | Google 格式检查 |
| `verify` | PMD 7 | 缺陷、复杂度、性能、安全 |
| `verify` | SpotBugs + FindSecBugs | 字节码缺陷与安全 |
| `verify` | JaCoCo | 覆盖率报告（1.0 先报告，不阻断） |

`mvn package` 不会触发全部门禁；以 `mvn verify` 为准。

## 4. Profile

| Profile | 作用 | 说明 |
|---|---|---|
| 默认 | 快速质量门禁 | 每个 PR 必跑 |
| `security` | Dependency-Check + CycloneDX SBOM | CI 或发版前跑；本地可选 |
| `p3c` | PMD 6 + Alibaba P3C 门禁 | 阻断；不要和 PMD 7 同一次调用 |

## 5. 版本与插件规则

- 所有插件版本在父 POM 的 `pluginManagement` 中显式声明。
- 禁止外部 SNAPSHOT 依赖；内部模块使用 `${project.version}`。
- 新增依赖前先确认 Spring Boot / Spring AI BOM 是否已管理版本。
- 构建脚本改动必须同时更新本文和 `docs/engineering/00-versions.md`。
