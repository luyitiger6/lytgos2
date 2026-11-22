# Roadmap

LytgOS2 is built with a simple philosophy: **slow is fast, focused and restrained**. Make the single-node runtime kernel solid and genuinely usable first. Then grow distributed capability on top of it, with each step verified by real usage.

The roadmap has three phases and a set of horizontal capabilities.

## Phase 1 — Single-node Runtime Kernel (current)

The first phase makes one node capable of running and managing a group of Agents:

- **Five core capabilities** — LLM providers, self-implemented ReAct loop, memory, tools, and REST service.
- **Config-as-Agent** — one directory with `AGENT.md` is one Agent, no backend code required.
- **Multi-Agent coexistence** — multiple independent Agents on the same instance.
- **MCP integration** — connect community MCP servers with zero code.
- **REST API** — expose capabilities over HTTP for any language.
- **Private deployment** — a single JAR on your own K8s, VMs, or bare metal; data stays in your domain.
- **Audit from day one** — `tool_invocations` and `llm_calls` written from the first version.

**Boundary:** this is a usable single-node Agent runtime kernel, not a complete enterprise Control Plane.

## Phase 2 — Distributed Foundation (planned)

Make the runtime horizontally scalable:

- Stateless instances: no local state in the LytgOS2 process.
- Externalized state: sessions, memory, and audit data stored outside the instance.
- Multi-replica deployment behind a load balancer.
- High availability with no single point of failure.
- Run and manage Agents at larger scale.

## Phase 3 — Cross-node Agent Collaboration (vision)

Use stable runtime semantics to support Agent collaboration across nodes:

- An Agent communication foundation with A2A.
- Cross-node Agent discovery and delegation.
- Reliable asynchronous collaboration without lost or duplicated messages.
- Multiple nodes of Agents working as one team.

## Horizontal capabilities

These capabilities are added across phases rather than in one big-bang release:

| Capability | Direction |
| --- | --- |
| Multi-tenancy | Organization/team isolation and ACLs |
| SSO / authentication | OIDC and enterprise identity integration |
| Complete audit | Audit query, export, and SIEM integration |
| Tool policy and RBAC | Per-Agent tool permissions and policy boundaries |
| Observability | Metrics, tracing, and operational dashboards |
| Web administration | Agent, session, schedule, and runtime management |
| Distributed state | Externalized session, memory, and audit storage |

## Development principles

- **Single-node first** — prove the runtime kernel before adding distribution.
- **Real usage over architecture diagrams** — every phase is validated by actual use.
- **Restraint** — keep the kernel small; add governance and distributed infrastructure only when the evidence requires it.
- **Private by default** — data stays in the enterprise's infrastructure, and every action is auditable.
