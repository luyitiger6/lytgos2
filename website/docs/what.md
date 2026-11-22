---
title: What is LytgOS2
titleTemplate: false
---

# What is LytgOS2

**LytgOS2 is an open-source, self-hosted Agent Operating System (Agent Harness OS) for enterprises.** You publish a task in natural language; the foundation decomposes it, assembles an Agent team, coordinates the work, and delivers a result. Every company can run its own Agents with natural language.

![LytgOS2 architecture overview](/images/lytgos-overview-en.svg)

## One directory = one Agent

The north-star formula is:

```text
natural language (md) + Memory + Tool + MCP (Connector) + Skill + Knowledge + Notify = one Agent
```

An Agent is a directory containing `AGENT.md`. The frontmatter is the runtime profile; the body is the task instruction. Drop the directory into `.lytgos/agents/<name>/` and it becomes a runnable Agent. One foundation runs many Agents, privately, without moving data out of your domain.

## Agent runtime vs Agent Harness OS

An Agent runtime runs one Agent: it calls the model, executes tools, manages context, and controls the reasoning loop. An Agent Harness OS manages a group of Agents: lifecycle, channels, shared memory, multi-tenancy and governance, and — in its distributed form — cross-node collaboration.

Think of a runtime as the environment for one process, and an Agent Harness OS as the layer that runs, schedules, and connects a group of processes. LytgOS2 is the latter.

## The execution path

```text
natural-language task → decompose → assemble Agent team → assign work
  → collaborate → deliver result → audit / trace
```

The Agent runtime is a self-implemented ReAct loop. Applications compose the models, Tools, MCP servers, memory, and channels they need; the platform provides the shared runtime, private deployment, sandbox, and audit beneath them.

## Five core capabilities

| Capability | Responsibility |
| --- | --- |
| **LLM Providers** | A Provider abstraction over major models; explicit provider mapping, runtime switching, and local inference. |
| **ReAct Loop** | Self-implemented reason → act → observe loop. The LLM chooses a tool, LytgOS2 executes it, feeds back the result, and loops until the final answer. |
| **Memory** | Session memory plus long-term memory. File-based long-term memory with keyword retrieval and a vector-ready interface. |
| **Tool System** | Built-in file, shell, and HTTP Tools; native MCP. Three extension tiers: AGENT.md + MCP, custom MCP server, native Tool. |
| **REST Service** | Every capability exposed over HTTP; any language can integrate. CLI, Web Service, and Scheduler share the same Profile and Session storage. |

## What the OS provides

- **Agent runtime** — LLM access, ReAct loop, context assembly, tool execution, and memory.
- **Agent lifecycle and collaboration** — one directory per Agent, multiple Agents per instance, and a path to cross-node A2A collaboration.
- **Private deployment and governance** — a single JAR on your own infrastructure, data stays in your domain, sandbox boundaries, environment-based secrets, and audit from day one.
- **Open standards** — MCP for tools, A2A for Agent collaboration, Agent Skills for reusable instructions, and REST for business systems.

## Current direction

LytgOS2 is deliberately staged: the single-node runtime kernel comes first, then a distributed foundation, then cross-node Agent collaboration. Multi-tenancy, SSO, complete audit, tool policy, observability, and web administration are horizontal capabilities added across phases. See the [Roadmap](/docs/roadmap).

## Why call it an OS

A model generates text. A harness gives an Agent context, tools, memory, execution, and boundaries. An Agent Harness OS gives many enterprise Agents the same runtime contracts and governance path. That shared layer is what makes a group of Agents operable — and what lets an enterprise adopt Agents without surrendering control of its data, tools, or operating rules.
