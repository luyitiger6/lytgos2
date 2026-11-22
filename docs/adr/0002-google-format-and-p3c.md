# ADR 0002：Google 格式为唯一权威，P3C 作为补充报告

- **状态**：已接受
- **日期**：2026-11-22
- **决策者**：项目所有者

## 背景

项目同时希望使用 Google Java 规范和阿里巴巴 Java 开发手册。两者定位不同：

- Google Java Format 解决“代码长什么样”：缩进、换行、import、空白。
- 阿里 P3C 解决“代码怎么写才对”：命名、并发、异常、集合、OOP。

规则重叠时以 Google 为准。但 P3C 2.1.1 是基于 PMD 6.15 的规则集，和 PMD 7 存在类路径/API 硬冲突，不能在同一次 Maven 调用中同时运行。

## 决策

1. **格式唯一权威**：Spotless + google-java-format 2.0（GOOGLE 风格）。任何格式争议以 `spotless:apply` 的结果为准。
2. **硬门禁**：Spotless、Checkstyle、PMD 7、SpotBugs + FindSecBugs、ArchUnit、测试。
3. **P3C 定位**：IDE 实时提示 + `./mvnw -Pp3c verify` 报告模式，默认不阻断。
4. **隔离**：`-Pp3c` 使用 PMD 6.55 + p3c-pmd 2.1.1，不能和默认 PMD 7 放在同一次 `mvn` 调用里。
5. **升级路径**：如果治理要求 P3C 阻断，另起独立 CI job，先建立 baseline、清理历史告警，再打开 `failOnViolation`；不直接污染主构建。

## 影响

- P3C 不是合并门禁，开发者靠 IDE 插件获得即时反馈。
- Checkstyle 只保留 Google 格式未覆盖的规则，避免和 Spotless 重复。
- P3C 在 Java 21 新语法上的误报按“窄范围 suppression + 注释”处理。
- 如果出现 PMD 7 兼容的 P3C 规则包或官方恢复维护，重新评估本 ADR。
