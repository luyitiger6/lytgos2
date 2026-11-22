---
title: LytgOS2 是什么
titleTemplate: false
---

# LytgOS2 是什么

**LytgOS2 是面向企业的开源、私有部署 Agent 操作系统（Agent Harness OS）。** 你用一句自然语言发布任务，底座把任务拆解、组织一支 Agent 团队、协调分工并交付结果。让每一家公司，都能用自然语言跑起来自己的 Agent。

![LytgOS2 整体架构](/images/lytgos-overview-zh.svg)

## 一个目录 = 一个 Agent

北极星公式：

```text
自然语言(md) + Memory + Tool + MCP(Connector) + Skill + 知识库 + Notify = 一个 Agent
```

一个 Agent 就是一个包含 `AGENT.md` 的目录。frontmatter 是运行时 Profile，正文是任务指令。把目录放进 `.lytgos/agents/<name>/`，它就是一个可运行的 Agent。一个底座运行一群 Agent，私有部署，数据不出域。

## Agent runtime 与 Agent Harness OS

Agent runtime 让一个 Agent 跑起来：调用模型、执行工具、管理上下文、控制推理循环。Agent Harness OS 管理的是一群 Agent：生命周期、统一渠道、共享记忆、多租户与治理，以及分布式形态下的跨节点协作。

可以把 runtime 类比成单个进程的执行环境，把 Agent Harness OS 类比成运行、调度并连接一群进程的那一层。LytgOS2 是后者。

## 执行路径

```text
自然语言任务 → 拆解 → 组织 Agent 团队 → 分配工作
  → 协作 → 交付结果 → 审计 / Trace
```

Agent runtime 是自实现的 ReAct 循环。应用可以组合需要的模型、Tool、MCP server、记忆和渠道；LytgOS2 在它们下方提供共享 Runtime、私有部署、沙箱与审计。

## 五大核心能力

| 能力 | 负责什么 |
| --- | --- |
| **对接 LLM** | 通过 Provider 抽象统一对接主流大模型；多 Provider 显式映射、运行时切换、支持本地推理。 |
| **ReAct 循环** | 自实现 reason → act → observe 循环。LLM 选择工具，LytgOS2 执行并回填结果，直到最终回答。 |
| **记忆** | 会话记忆 + 长期记忆。长期记忆以文件形态存储，关键词检索，接口预留向量检索升级空间。 |
| **工具体系** | 内置文件、Shell、HTTP Tool；原生 MCP。三档扩展：AGENT.md + MCP、自写 MCP server、原生 Tool。 |
| **对外服务** | 所有能力通过 HTTP 暴露，任何语言都可集成。CLI、Web Service、Scheduler 共享同一套 Profile 与 Session 存储。 |

## 底座提供什么

- **Agent runtime** — LLM 接入、ReAct 循环、上下文组装、Tool 执行、记忆。
- **Agent 生命周期与协作** — 一个目录一个 Agent、多 Agent 并存，并为跨节点 A2A 协作留好路径。
- **私有部署与治理** — 单 JAR 装在企业自己的基础设施上，数据不出域，沙箱边界、环境变量凭证、第一天写入审计。
- **开放标准** — Tool 用 MCP，Agent 协作用 A2A，技能用 Agent Skills，业务系统用 REST。

## 当前方向

LytgOS2 分阶段推进：先做单机运行时内核，再做底座分布式，最后走向跨节点 Agent 协作。多租户、SSO、完整审计、工具策略、可观测和 Web 管理是随各阶段逐步补齐的横向能力。详见[路线图](/zh/docs/roadmap)。

## 为什么叫 OS

模型生成文本；harness 给 Agent 上下文、工具、记忆、执行环境和边界；Agent Harness OS 让一群企业 Agent 共享同一套运行契约和治理路径。正是这层共享能力，让一群 Agent 可以被运行和管理，也让企业可以在不交出数据、Tool 和运行规则控制权的前提下采用 Agent。
