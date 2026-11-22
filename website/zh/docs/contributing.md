# 贡献指南

> 欢迎你。对，就是你。

LytgOS2 是**我们**的项目，而且才刚刚开始。这里没有很多规矩，也不需要你先成为专家。真正重要的只有一件事：**你愿意动起来。** 从你开第一个 PR、看第一个 diff、改一个错别字开始，你就已经是贡献者了。

我们希望通过这个项目一起做三件事：

1. **动起来，学 AI 编程，走进开源。** 很多人想给开源做贡献，但一直没迈出第一步。这里就是迈出第一步的地方。
2. **学着成为 maintainer。** 不只是提交代码——评审别人的代码、帮助别人、守住质量、带新人。这些能力比写代码更值钱。
3. **把它写进简历。** 我们真心希望项目成长。等项目成长起来，参与过并维护过它，会是你简历上实打实的一行。

所以不要犹豫，也不要担心自己做得不够好。**先拿出来，我们一起把它变好。**

## 你可以贡献什么

写核心代码不是唯一的贡献方式。下面这些同样欢迎：

- **代码**——修 bug、加功能、写内置 Tool、接一个 MCP server、优化性能。
- **文档**——补缺口、改错别字、把不清楚的段落写清楚、中英文互译。
- **测试**——补用例、复现和报告 bug、提高覆盖率。
- **Agent / Skill 示例**——写一个有意思的 Agent 目录或 Skill，放进示例里让别人学习。
- **网站**——首页和文档站在 `website/`；版式、文案和翻译都欢迎。
- **Issue**——报 bug、提想法、参与讨论。哪怕只是“这段我没看懂”也是很有价值的反馈。
- **评审**——去看别人的 PR，留一句评论，点一个 LGTM。这是成为 maintainer 的第一步。

> 不知道从哪开始？在 [Issues](https://github.com/luyitiger6/lytgos2/issues) 里找 `good first issue`，或者直接开一个 issue 说“我想帮忙，从哪里开始？”

## 用 AI，但对结果负责

**我们欢迎、而且强烈鼓励你在贡献过程中使用 AI。** 写代码、写文档、写测试、调试、理解陌生模块——Claude Code、Copilot、Cursor，顺手哪个用哪个。LytgOS2 本身就是一个“和 AI 一起把事情做出来”的项目，帮你走进 AI 编程也是我们的目标之一。

但有一条底线：

> **AI 只是工具。真正重要的是你能对结果负责。**

这意味着你提交的每一个 PR——不管是不是 AI 写的——都是**你的** PR：

- **你理解它。** 不要提交自己看不懂的代码。AI 生成之后，你应该能解释为什么这样写。
- **你验证过它。** 在本地构建、运行、检查行为——不是“AI 说它能跑”。
- **你负责它。** Review 意见找到你，回归 bug 也是你的。“这是 AI 写的”不是理由。

一句话：**AI 让你更快更强，但名字和责任是你的。**

## 环境准备

LytgOS2 是 Java 21 + Spring Boot 的 Maven 多模块项目。你需要：

- **JDK 21+**（必须——虚拟线程是运行时基础）
- **Maven 3.9+**
- **Node.js 18+**（只在修改 `website/` 下的 VitePress 网站时需要）

```bash
# clone 你的 fork
git clone https://github.com/<你的用户名>/lytgos2.git
cd lytgos2

# 构建可执行 JAR
mvn clean package

# 本地运行（先按快速开始配置 Provider）
java -jar lytgos-boot/target/lytgos-boot-*.jar serve
```

网站开发：

```bash
cd website
npm install
npm run docs:dev
```

完整的构建与配置说明见[快速开始](./quick-start)。

## 贡献流程（标准 GitHub 流程）

如果你第一次参与开源，这个流程和几乎所有 GitHub 项目一样——学一次，到处都能用。不熟悉的话先读[GitHub 贡献入门](./github-workflow)。

1. **Fork** 这个仓库到你自己的账号。
2. 从 `main` 切一个语义清晰的**分支**。

   ```bash
   git checkout -b feat/add-weather-agent
   ```

3. **先讨论再动手**：涉及行为或范围变化时，先在 issue 里讨论并记录方案与验收标准，再开始实现。小而独立的修复可以直接开 PR。
4. **写代码**：小步、聚焦。一个 PR 只做一件事——好评审、好合并。
5. **本地构建**：推送前 `mvn clean package` 必须成功。
6. **提交**：写清晰的 commit message（见下文）。
7. **推送并开 PR**：说明为什么改、改了什么、怎么验证；完成 issue 时用 `Closes #123` 关联。
8. **参与 Review**：maintainer 会看你的 PR。一起改到可以合并——这是学得最快的方式。
9. **合并**：有 approve 且本地构建通过后，maintainer 合并。🎉

## Commit 约定

使用 [Conventional Commits](https://www.conventionalcommits.org/) 风格，让历史一眼能看懂：

```text
<type>(<optional scope>): <short description>

feat(tool): add a built-in tool
fix(core): handle an empty tool argument
docs: update the quick start
test(memory): cover archive recall
chore: bump version to 0.1.1
```

常用类型：`feat`、`fix`、`docs`、`test`、`refactor`、`chore`。

## AI 辅助写 Issue 和 PR

AI 可以帮你选择 issue 类型、根据你提供的信息起草 issue、实现已经达成一致的方案、整理 PR 描述。Issue 是事实来源：开放问题在 issue 里讨论，方案确定后再让 AI 实现。

AI 不得编造缺失的复现步骤、环境信息、范围、验收标准或测试结果。让它标出未知项，或者来问你。最终要检查实际改动，并对 issue 和 PR 负责。

## Issue 类型与标签

每个请求选择一个 issue 类型。标签是仓库配置，不是 GitHub 的固定标准。

| 类型 | 标题前缀 | 标签 |
| --- | --- | --- |
| Bug | `[bug]` | `bug` |
| 功能或增强 | `[feature]` | `enhancement` |
| 文档 | `[docs]` | `documentation` |
| 提案 | `[proposal]` | `proposal` |
| 维护任务 | `[task]` | `task` |
| 问题或支持 | `[question]` | `question` |

## 构建门禁

当前 1.0 的门禁是 **`mvn clean package`**。测试框架、静态检查和 CI 会随着实现逐步补齐；补上后这一节会同步更新。在此之前，请在本地完成构建，并实际验证你修改的行为。

以下**架构原则**不可协商：

- ReAct 循环自实现；Spring AI 只用于 Provider 协议转换和 `@Tool` JSON Schema 生成。
- 禁用 Spring AI 的自动 Tool 执行；Tool 调度归 `ReActLoop` + `ToolExecutor`。
- Provider 映射必须显式；不要按类型扫描所有 `ChatModel` Bean。
- `tool_invocations` 和 `llm_calls` 从第一天起写入。
- Sandbox 使用白名单，不使用 Java `SecurityManager`。
- 密钥来自环境变量或外部配置，绝不写进 `AGENT.md` 或仓库。

修改核心时先看 `CLAUDE.md` 和 `docs/ReleaseBaseline.md`；不确定就在 issue/PR 里问。

## 行为准则

一句话：**对人友善，对事认真。**

项目才刚刚开始。我们最担心的不是代码不够好，而是有人因为怕显得笨而不参与。所以：

- 评审代码，不评审人；说具体、可执行的意见。
- 提问不丢人。每个 maintainer 都是从“这是什么？”开始的。
- 欢迎新人。多一句鼓励，就多一个人留下来。

## 联系我们

- **Issues**：报 bug、提想法 → [github.com/luyitiger6/lytgos2/issues](https://github.com/luyitiger6/lytgos2/issues)
- **Pull Requests**：开始贡献 → [github.com/luyitiger6/lytgos2/pulls](https://github.com/luyitiger6/lytgos2/pulls)
- **社区**：Lytg-labs 是围绕项目的 AI coding 驱动社区，站点正在建设中。

想更进一步成为 maintainer？看 [Maintainer 指南](./maintainer)。第一次在 GitHub 协作？看 [GitHub 贡献入门](./github-workflow)。
