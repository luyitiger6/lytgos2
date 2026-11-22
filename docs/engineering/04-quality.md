# 质量规范

## 1. 两层规范，Google 优先

- **格式层：Google Java Format**，由 Spotless 执行，是格式的唯一权威。
- **编码规约层：Alibaba P3C**，作为阻断检查，覆盖命名、并发、异常、集合等“怎么写才对”。
- 两者规则冲突时，以 Google 为准。P3C 的同类规则通过 suppression 或 profile 裁剪，不反向修改 Google 格式。

## 2. 硬门禁

以下检查失败即阻断合并：

| 检查 | 工具 | 说明 |
|---|---|---|
| 格式 | Spotless + gJF 2.0（GOOGLE） | `spotless:check` |
| 命名/import/行宽 | Checkstyle 10.21.4 | 只做格式之外的规则 |
| 缺陷/复杂度/性能 | PMD 7.17.0 | `category/java/*` |
| P3C 规则 | PMD 6.55 + p3c-pmd 2.1.1 | 独立 `p3c` profile / CI job；违规阻断 |
| 字节码缺陷 | SpotBugs 4.10.4.0 | `effort=Max`, `threshold=Medium` |
| 安全缺陷 | FindSecBugs 1.14.0 | 挂在 SpotBugs 上 |
| 模块依赖 | ArchUnit 1.5.1 | 无环、方向、禁止标准流 |
| 测试 | JUnit 5 / AssertJ | 每个模块至少一个可运行测试 |
| 覆盖率 | JaCoCo | 1.0 先报告，不阻断；逐模块提高后再设阈值 |

## 3. P3C 的定位

P3C 2.1.1 使用 PMD 6.55.0，不能和 PMD 7 在同一次 Maven 调用中共存。因此：

- P3C 是 **IDE 实时提示 + `-Pp3c` 门禁 + CI `p3c` job**，违规阻断合并。
- `./mvnw -Pp3c verify` 使用 PMD 6.55 + P3C，`failOnViolation=true`。
- 不要把 `-Pp3c` 和默认 `verify` 放在同一次 `mvn` 调用里。
- 误报使用窄范围 suppression，并在 PR 说明原因；不要全局关闭规则。
- P3C 使用 PMD 6.55.0，默认 ASM 无法读取 Java 21 class 文件；`p3c` profile 覆盖为 ASM 9.7，确保分析真正执行。CI 的 `p3c` job 会上传 `pmd.xml`，报告中出现 `<error>` 或违规时都会失败。

## 4. 规则冲突处理

1. 格式冲突：以 Spotless/gJF 为准，删除或抑制 P3C/Checkstyle 的重复格式规则。
2. 命名冲突：以 Checkstyle + P3C 的命名规则为准；两者重复时保留 Checkstyle。
3. 误报：优先加窄范围 suppression，附注释说明原因；不要全局关闭规则。
4. 新规则：先在报告模式观察一个迭代，再决定是否升级为硬门禁。

## 5. 本地命令

```bash
./mvnw spotless:apply      # 自动修格式
./mvnw spotless:check      # 只检查
./mvnw verify              # 全量硬门禁
./mvnw -Pp3c verify        # P3C 阻断门禁
```
