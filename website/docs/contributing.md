# Contributing Guide

> Welcome. Yes, you.

LytgOS2 is **our** project, and it's just getting started. There aren't many rules here, and you don't need to be an expert first. There's really only one thing that matters: **you're willing to get moving.** The moment you open a PR, review a diff, or fix a typo, you're already a contributor.

We want to do three things together through this project:

1. **Get moving, learn AI programming, step into open source.** A lot of people want to contribute to open source but never take the first step. This is the place to take it.
2. **Learn to become a maintainer.** Not just committing code — reviewing others' code, helping people, guarding quality, mentoring newcomers. Those skills are worth more than writing code.
3. **Put it on your résumé.** We genuinely hope this project grows. When it does, having contributed to and maintained it is a real, concrete line on your résumé.

So don't hesitate, and don't worry that your work isn't good enough. **Put it out there — we'll improve it together.**

## What you can contribute

Writing core code isn't the only kind of contribution. All of these are equally welcome:

- **Code** — fix bugs, add features, write a built-in Tool, wire up a new MCP server, improve performance.
- **Docs** — fill gaps, fix typos, make an unclear section clear, translate between English and Chinese.
- **Tests** — add cases, reproduce and report bugs, raise coverage.
- **Agent / Skill examples** — write an interesting Agent directory or Skill and add it to the examples so others can learn from it.
- **Website** — the homepage and docs live in `website/`; layout, copy, and translation contributions are welcome.
- **Issues** — report a bug, propose an idea, join a discussion. Even "I didn't understand this part" is valuable feedback.
- **Reviews** — go look at someone else's PR, leave a comment, drop an LGTM. This is the first step toward becoming a maintainer.

> Not sure where to start? Look for issues labeled `good first issue` in [Issues](https://github.com/luyitiger6/lytgos2/issues), or just open an issue saying "I'd like to help — where do I start?"

## Use AI — but own the result

**You're welcome, and in fact strongly encouraged, to use AI throughout your contribution.** Writing code, docs, tests, debugging, understanding an unfamiliar module — go ahead and use Claude Code, Copilot, Cursor, or whatever fits your hand. LytgOS2 is itself a "get things done together with AI" project, and helping you step into AI programming is one of our goals.

But there's one line:

> **AI is just a tool. What actually matters is that you can own the result.**

That means every PR you submit — whether or not AI wrote it — is **your** PR:

- **You understand it.** Don't submit code you can't follow yourself. After the AI generates it, you should be able to explain why it's written that way.
- **You verified it.** Build it locally, run it, and check the behavior — not "the AI said it works".
- **You own it.** Review feedback comes to you; regression bugs are yours. "The AI wrote it" is not an excuse.

In short: **AI makes you faster and stronger, but the name and the responsibility are yours.**

## Setting up

LytgOS2 is a Java 21 + Spring Boot Maven multi-module project. You'll need:

- **JDK 21+** (required — virtual threads are core to the runtime)
- **Maven 3.9+**
- **Node.js 18+** only if you're working on the VitePress website in `website/`

```bash
# clone your fork
git clone https://github.com/<your-username>/lytgos2.git
cd lytgos2

# build the executable JAR
mvn clean package

# run locally (configure a Provider first, per Quick Start)
java -jar lytgos-boot/target/lytgos-boot-*.jar serve
```

For the website:

```bash
cd website
npm install
npm run docs:dev
```

See [Quick Start](./quick-start) for build and configuration details.

## Contribution flow (standard GitHub flow)

If you're new to open source, this flow is the same as on almost every GitHub project — learn it once, use it forever. New to it? Read [GitHub Contribution Basics](./github-workflow) first.

1. **Fork** this repo to your own account.
2. **Branch** off `main` with a semantic name.

   ```bash
   git checkout -b feat/add-weather-agent
   ```

3. **Discuss proposed work first** when it changes behavior or scope. Keep the proposal and its discussion in an issue; once the intended solution and acceptance criteria are agreed, implementation can start. Small, self-contained fixes can go directly to a PR.
4. **Write code** in small, focused steps. One PR does one thing — easy to review, easy to merge.
5. **Build locally**: `mvn clean package` must succeed before you push.
6. **Commit** with a clear message (see below).
7. **Push and open a PR**: explain why, what changed, and how you verified it; link the issue with `Closes #123` when the PR completes it.
8. **Engage in review**: a maintainer will look at your PR. Address the feedback together — this is the fastest way to learn.
9. **Merge**: with an approval and a passing local build, a maintainer merges it. 🎉

## Commit conventions

Use [Conventional Commits](https://www.conventionalcommits.org/) style so history reads at a glance:

```text
<type>(<optional scope>): <short description>

feat(tool): add a built-in tool
fix(core): handle an empty tool argument
docs: update the quick start
test(memory): cover archive recall
chore: bump version to 0.1.1
```

Common types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`.

## AI-assisted issue and PR drafting

AI can help select an issue type, draft the issue from information you provide, implement an agreed proposal, and prepare a PR description. Keep the issue as the source of truth: discuss open questions there and record the agreed solution before asking AI to implement it.

AI must not invent missing reproduction steps, environment details, scope, acceptance criteria, or test results. Ask it to mark unknowns or ask you. Review the actual changes and own the final issue and PR.

## Issue types and labels

Choose one issue type for each request. Labels are repository configuration, not a fixed GitHub standard.

| Type | Title prefix | Label |
| --- | --- | --- |
| Bug | `[bug]` | `bug` |
| Feature or enhancement | `[feature]` | `enhancement` |
| Documentation | `[docs]` | `documentation` |
| Proposal | `[proposal]` | `proposal` |
| Maintenance task | `[task]` | `task` |
| Question or support | `[question]` | `question` |

## Build gates

The current 1.0 gate is **`mvn clean package`**. The test harness, static analysis, and CI are being added as the implementation lands; this section will grow with them. Until then, run the build locally and exercise the changed behavior before opening a PR.

A few **architectural principles** are non-negotiable:

- The ReAct loop is self-implemented; Spring AI is used only for Provider protocol conversion and `@Tool` JSON Schema generation.
- Automatic Spring AI tool execution is disabled; Tool scheduling belongs to `ReActLoop` + `ToolExecutor`.
- Provider mapping is explicit; do not scan all `ChatModel` beans by type.
- `tool_invocations` and `llm_calls` are written from day one.
- Sandboxing uses whitelists, not Java `SecurityManager`.
- Secrets come from environment variables or external config, never `AGENT.md` or the repository.

When touching the core, check `CLAUDE.md` and `docs/ReleaseBaseline.md`; if unsure, ask in an issue or PR.

## Code of conduct

In one line: **be kind to people, be serious about the work.**

We're just starting out. The thing we fear most isn't code that isn't good enough — it's someone who won't take part because they're afraid to look foolish. So:

- Review the code, not the person; be specific and actionable.
- Asking questions isn't embarrassing. Every maintainer started at "what is this?".
- Welcome newcomers. One more word of encouragement means one more person who stays.

## Reach us

- **Issues**: report bugs, propose ideas → [github.com/luyitiger6/lytgos2/issues](https://github.com/luyitiger6/lytgos2/issues)
- **Pull Requests**: start contributing → [github.com/luyitiger6/lytgos2/pulls](https://github.com/luyitiger6/lytgos2/pulls)
- **Community**: Lytg-labs is the AI-coding-driven community around the project; its site is under construction.

Want to go further and become a maintainer? See the [Maintainer Guide](./maintainer). First time collaborating on GitHub? See [GitHub Contribution Basics](./github-workflow).
