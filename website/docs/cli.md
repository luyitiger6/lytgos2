# CLI

The CLI is the `lytgos <command>` entry point (Picocli). 1.0 has 12 commands.

## Commands (12)

| Category | Command | Purpose |
| --- | --- | --- |
| Start & status | `lytgos init` | Create the `.lytgos/` workspace and default bootstrap files |
| Start & status | `lytgos status` | Print workspace and data-file status |
| Start & status | `lytgos chat --profile <name>` | Interactive chat with an Agent |
| Start & status | `lytgos serve` | Start the REST API server (default port 8080) |
| Start & status | `lytgos gateway` | Run as a resident daemon |
| Profile | `lytgos profile list` | List Agent directories / Profiles |
| Profile | `lytgos profile create <name>` | Create an Agent directory and `AGENT.md` |
| Profile | `lytgos profile show <name>` | Show a Profile |
| Profile | `lytgos profile delete <name>` | Delete an Agent directory |
| Query | `lytgos provider list` | List configured providers |
| Query | `lytgos tool list` | List registered Tools |
| Query | `lytgos session list` | List Sessions |

`lytgos agent import` belongs to the extension phase and is not part of the 12 commands.

## Light and runtime commands

- **Light commands** (`init`, `status`, `profile list/create/show/delete`) operate on files directly and do not boot Spring.
- **Runtime commands** (`chat`, `serve`, `gateway`, `provider list`, `tool list`, `session list`) need the runtime and boot Spring.

## Workspace

```text
.lytgos/
├── agents/            # one subdirectory = one Agent (AGENT.md + skills/ + scripts/)
├── skills/            # global Skill library
├── memory/
│   └── MEMORY.md      # long-term memory
├── sessions/          # Session data
├── logs/              # structured logs
├── output/            # Agent artifacts
├── mcp_servers.yaml   # MCP configuration
├── AGENTS.md          # bootstrap: project-level behavior notes
├── SOUL.md            # bootstrap: default personality
├── USER.md            # bootstrap: user preferences
└── lytgos.db          # SQLite database
```

## Examples

```bash
# initialize the workspace
lytgos init

# check workspace status
lytgos status

# create and chat with an Agent
lytgos profile create weather-daily
lytgos chat --profile weather-daily

# server and daemon modes
lytgos serve
lytgos gateway

# inspect runtime state
lytgos profile list
lytgos provider list
lytgos tool list
lytgos session list
```

## Workspace root

The default workspace root is `.lytgos`. Override it with:

- `-Dlytgos.root=<path>` for Java system properties
- `LYTGOS_ROOT=<path>` for the environment

Light commands read `LYTGOS_ROOT` directly; runtime commands also resolve it through Spring relaxed binding.

## 1.0 boundaries

The CLI does not include `agent import`, runtime cron create/update/delete, a web admin console, or authentication. See the [Roadmap](/docs/roadmap) for phased capabilities.
