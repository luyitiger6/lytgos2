# ADR 0002：Google 格式为唯一权威，P3C 作为阻断门禁

- **状态**：已接受（2026-11-23 修订：P3C 改为阻断）
- **日期**：2026-11-22
- **决策者**：项目所有者

## 背景

项目同时希望使用 Google Java 规范和阿里巴巴 Java 开发手册。两者定位不同：

- Google Java Format 解决“代码长什么样”：缩进、换行、import、空白。
- 阿里 P3C 解决“代码怎么写才对”：命名、并发、异常、集合、OOP。

规则重叠时以 Google 为准。但 P3C 2.1.1 使用 PMD 6.55.0 的规则集，和 PMD 7 存在类路径/API 硬冲突，不能在同一次 Maven 调用中同时运行。PMD 6.55 默认的 ASM 读不了 Java 21 class 文件，`p3c` profile 覆盖为 ASM 9.7。

## 决策

1. **格式唯一权威**：Spotless + google-java-format 2.0（GOOGLE 风格）。任何格式争议以 `spotless:apply` 的结果为准。
2. **硬门禁**：Spotless、Checkstyle、PMD 7、SpotBugs + FindSecBugs、ArchUnit、测试。
3. **P3C 定位**：IDE 实时提示 + `./mvnw -Pp3c verify` 阻断门禁；违规阻断合并。
4. **隔离**：`-Pp3c` 使用 PMD 6.55 + p3c-pmd 2.1.1，并通过 ASM 9.7 读取 Java 21 class 文件；不能和默认 PMD 7 放在同一次 `mvn` 调用里。
5. **升级路径**：已建立 baseline 并清理历史告警，CI `p3c` job 打开 `failOnViolation=true`；新增规则先在报告模式观察一个迭代。

## 影响

- P3C 是合并门禁，违规会阻断合并；IDE 插件仍提供即时反馈。
- Checkstyle 只保留 Google 格式未覆盖的规则，避免和 Spotless 重复。
- P3C 在 Java 21 新语法上的误报按“窄范围 suppression + 注释”处理。
- 如果出现 PMD 7 兼容的 P3C 规则包或官方恢复维护，重新评估本 ADR。
