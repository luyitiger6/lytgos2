---
title: Why LytgOS2
titleTemplate: false
---

# Why LytgOS2

## The hard part starts after a model can answer

Connecting a model to a prompt and a few tools is getting easier. The hard part is making an Agent work inside a company: reading internal knowledge, touching business systems, staying inside a budget, proving what it did, and improving without building a different stack for every application.

That work does not belong inside every application. It belongs in a shared harness.

## Four barriers between a demo and production

1. **Defining an Agent requires code.** The people who understand the business cannot build the Agent themselves.
2. **Cloud platforms take the data out.** Compliance and data-residency rules block adoption.
3. **Execution is a black box.** No audit, no whitelist, no approval — enterprises do not dare put it into production.
4. **Running one Agent is easy; running a group is hard.** Nobody provides the "operating system for a group of Agents" layer.

LytgOS2 removes these barriers with natural-language Agent definitions, private deployment, end-to-end audit and sandboxing, and lifecycle and governance for a whole team of Agents.

## The bottleneck is the runtime environment

The limiting factor for reliable Agents is usually not the model. It is the environment: can the Agent get the right context, use controlled tools, run in isolation, prove what it did, and collaborate across nodes without losing or duplicating messages?

That is why LytgOS2 is not another Agent. It is the foundation that lets a group of Agents run and collaborate reliably.

## Agent runtime vs Agent Harness OS

An Agent runtime runs one Agent. An Agent Harness OS runs and manages a group of Agents: lifecycle, channels, shared memory, multi-tenancy and governance, and — in its distributed form — cross-node collaboration. LytgOS2 is the OS layer.

## What enterprises need to control

- **Context** — the right business knowledge, current data, and task history.
- **Environment** — a controlled place to execute work and connect to enterprise systems.
- **Capability** — models, tools, MCP servers, Skills, and credentials that can be extended without unlimited exposure.
- **Governance** — identity, authorization, policy, approval, sandboxing, audit, and cost boundaries.
- **Proof and learning** — artifacts, audit evidence, trace, evaluation, and feedback.

When every team implements these alone, the enterprise gets a collection of demos and scripts. Rules drift, costs are opaque, and an incident in one application teaches nothing to the next.

## A common foundation, different Agent teams

The same OS can support different Agent teams because the business layer stays outside the kernel:

| Agent team | Adds on top of LytgOS2 |
| --- | --- |
| Engineering | Code repositories, coding Agents, CI, review, and test evidence |
| Support | Knowledge, CRM, ticketing, approval, and human handoff |
| Operations | Runbooks, observability, controlled actions, and incident evidence |
| Competitive intelligence | Research, analysis, writing, and scheduled delivery |

LytgOS2 supplies the runtime and governance semantics they share. Each team owns the business workflow and acceptance standard that makes it useful.

## The direction

Start with a usable single-node runtime kernel. Then add a distributed foundation and cross-node Agent collaboration, with each phase verified by real usage. See the [Roadmap](/docs/roadmap).
