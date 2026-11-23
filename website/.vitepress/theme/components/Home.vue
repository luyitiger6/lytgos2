<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'

const { lang } = useData()
const isZh = computed(() => lang.value === 'zh-CN')
const t = (zh, en) => isZh.value ? zh : en

const capabilities = computed(() => [
  {
    num: '01',
    title: t('对接 LLM', 'LLM Providers'),
    desc: t(
      '通过 Provider 抽象统一对接主流大模型，Agent 不感知具体厂商；多 Provider 显式映射、运行时切换无锁定，并支持本地推理。',
      'Unified access to major LLMs through a Provider abstraction. Agents stay vendor-agnostic; explicit provider mapping, runtime switching without lock-in, and local inference.'
    ),
    code: `lytgos.providers:
  - name: deepseek
    api-key: DEEPSEEK_API_KEY       # env placeholder
  - name: ollama
    base-url: http://localhost:11434`,
  },
  {
    num: '02',
    title: t('自实现 ReAct 循环', 'Self-implemented ReAct Loop'),
    desc: t(
      'LLM 决定是否调用工具、调用哪个；LytgOS2 执行并回填结果，循环直到最终回答或达到最大迭代次数。核心循环自己实现，行为完全可控。',
      'The LLM decides whether and which tool to call; LytgOS2 executes it and feeds back the result, looping until the final answer or max iterations. The core loop is self-implemented and fully controllable.'
    ),
    code: `User message
  → PromptBuilder: system + memory + history + tools
  → ProviderService.call()
  → [Tool call?] → SandboxChecker → ToolExecutor
  → append result → loop
  → [Final reply] → return`,
  },
  {
    num: '03',
    title: t('记忆', 'Memory'),
    desc: t(
      '会话记忆 + 长期记忆两层。长期记忆以文件形态存储，按关键词检索，接口预留向量检索升级空间；核心记忆不截断、不参与检索。',
      'Session memory plus long-term memory. Long-term memory is file-based with keyword retrieval and a vector-ready interface; core memory is never truncated or searched.'
    ),
    code: `.lytgos/memory/MEMORY.md

## 核心记忆
- 周报每周五 17:00 发出

## 归档记忆
- 2026-11: 发布窗口为周四晚间`,
  },
  {
    num: '04',
    title: t('工具体系', 'Tool System'),
    desc: t(
      '内置文件、Shell、HTTP 等基础 Tool，原生对接 MCP。三档扩展：零代码写 AGENT.md 并复用 MCP server、轻代码自写 MCP server、重代码实现原生 Tool。',
      'Built-in file, shell, and HTTP tools with native MCP. Three extension tiers: zero-code AGENT.md + MCP server, light-code custom MCP server, heavy-code native tools.'
    ),
    code: `# Zero-code: AGENT.md + MCP server
# Light-code: custom MCP server
# Heavy-code: native Tool bean

Sandbox: file / command / domain whitelists
Audit: tool_invocations written on every call`,
  },
  {
    num: '05',
    title: t('对外服务', 'REST Service'),
    desc: t(
      '所有能力通过 REST API 对外暴露，业务系统用 HTTP 接入，任何开发语言都可集成；Web Service 与 CLI、Scheduler 共享同一套 Session 与 Profile。',
      'Every capability is exposed through REST APIs; business systems integrate over HTTP in any language. The Web Service shares Session and Profile storage with CLI and Scheduler.'
    ),
    code: `❯ lytgos serve

/api/v1/**     REST API
/swagger-ui    OpenAPI docs`,
  },
  {
    num: '06',
    title: t('私有部署与安全', 'Private Deployment & Security'),
    desc: t(
      '单可执行 JAR 装在企业自己的 K8s、虚拟机或物理机上，数据不出域。Tool 调用经路径、命令、域名白名单校验，凭证通过环境变量注入，全链路审计从第一天写入。',
      'A single executable JAR runs on your K8s, VMs, or bare metal; data stays in your domain. Tool calls pass path, command, and domain whitelists; secrets come from environment variables; audit is written from day one.'
    ),
    code: `java -jar lytgos-boot/target/lytgos-boot-*.jar serve

# Data stays in your infrastructure
# .lytgos/lytgos.db + workspace files`,
  },
])

const scenarios = computed(() => [
  {
    num: '01',
    title: t('运维助手', 'DevOps Agent'),
    desc: t('读日志、执行 Shell、监控服务，跨对话记住你的运维偏好。', 'Reads logs, runs shell commands, monitors services. Remembers infra preferences across sessions.'),
  },
  {
    num: '02',
    title: t('竞品情报 Agent 团队', 'Competitive Intelligence Team'),
    desc: t('用一句自然语言发布任务，研究、分析、写作、定时四个 Agent 分工协作，周五自动交付汇报。', 'One natural-language task: researcher, analyst, writer, and scheduler Agents collaborate and deliver the report on Friday.'),
  },
  {
    num: '03',
    title: t('客服助手', 'Customer Service'),
    desc: t('通过 REST API 接入客服渠道，记住历史交互，必要时触发人工升级。', 'Handles queries via REST API, recalls past interactions, escalates when needed.'),
  },
  {
    num: '04',
    title: t('知识管理助手', 'Knowledge Management'),
    desc: t('索引内部文档，回答问题，将学到的事实写入长期记忆。', 'Indexes internal docs, answers questions, persists learned facts to long-term memory.'),
  },
  {
    num: '05',
    title: t('代码审查 Agent', 'Code Review Agent'),
    desc: t('通过 MCP 审查 PR，评论 Issue，在记忆中追踪审查模式。', 'Reviews PRs via MCP, comments on issues, tracks review patterns in memory.'),
  },
  {
    num: '06',
    title: t('HR 助手', 'HR Assistant'),
    desc: t('回答 HR 问题、安排面试、检索政策文档，通过 REST API 集成企业系统。', 'Answers HR queries, schedules interviews, retrieves policy docs via REST API.'),
  },
  {
    num: '07',
    title: t('告警监控 Agent', 'Alert Monitor'),
    desc: t('轮询监控 API，用 LLM 分析异常，发送结构化报告。', 'Polls monitoring APIs, analyzes anomalies with LLM, sends structured reports.'),
  },
  {
    num: '08',
    title: t('企业研发自动化', 'Engineering Automation'),
    desc: t('把代码、知识、工具与研发流程组合成可治理的研发自动化应用。', 'Compose code, knowledge, tools, and engineering workflows into governed automation applications.'),
  },
])

const roadmapPhases = computed(() => [
  {
    phase: t('阶段一', 'Phase 1'),
    status: t('当前', 'CURRENT'),
    active: true,
    title: t('单机运行时内核', 'Single-node Runtime Kernel'),
    items: [
      t('五大核心能力：LLM、ReAct、记忆、Tool、REST 服务', 'Five core capabilities: LLM, ReAct, Memory, Tools, REST service'),
      t('一个目录 = 一个 Agent，多 Agent 并存', 'One directory = one Agent; multiple Agents per instance'),
      t('配置即 Agent、MCP 接入、私有部署', 'Config-as-Agent, MCP integration, private deployment'),
    ],
  },
  {
    phase: t('阶段二', 'Phase 2'),
    status: t('规划中', 'PLANNED'),
    active: false,
    title: t('底座分布式', 'Distributed Foundation'),
    items: [
      t('节点无状态化、状态外置', 'Stateless nodes, externalized state'),
      t('多副本部署与高可用', 'Multi-replica deployment and high availability'),
      t('更大规模的 Agent 运行与管理', 'Run and manage Agents at larger scale'),
    ],
  },
  {
    phase: t('阶段三', 'Phase 3'),
    status: t('愿景', 'VISION'),
    active: false,
    title: t('跨节点 Agent 协作', 'Cross-node Agent Collaboration'),
    items: [
      t('引入 Agent 通信底座，对接 A2A', 'Agent communication foundation with A2A'),
      t('跨节点发现、委托、可靠异步协同', 'Cross-node discovery, delegation, reliable async collaboration'),
      t('让多节点上的 Agent 像一支团队', 'Agents across nodes work as one team'),
    ],
  },
])
</script>

<template>
  <div class="home">

    <!-- ── HERO ── -->
    <section class="hero">
      <div class="hero-inner">
        <p class="hero-eyebrow">
          <span class="eyebrow-comment">// </span>{{ t('开源 · 私有部署 · Apache 2.0', 'open-source · self-hosted · Apache 2.0') }}
        </p>

        <h1 class="hero-headline">
          <span class="headline-tag">{{ t('企业 Agent 操作系统 · Agent Harness OS', 'The Enterprise Agent OS · Agent Harness OS') }}</span><br>
          <span class="headline-white">{{ t('用一句自然语言，发布任务。', 'Publish a task in natural language.') }}</span><br>
          <span class="headline-amber">{{ t('让一支 Agent 团队交付结果。', 'Let a team of Agents deliver the result.') }}</span>
        </h1>

        <p class="hero-sub">
          {{ t(
            'LytgOS2 是企业私有部署的 Agent 操作系统。它把任务拆解、组织 Agent 团队、分工协作，并把上下文、工具、记忆、沙箱和审计统一在同一套底座中。让每一家公司，都能用自然语言跑起来自己的 Agent。',
            'LytgOS2 is the self-hosted Agent Operating System for enterprises. It decomposes a task, assembles an Agent team, coordinates the work, and unifies context, tools, memory, sandboxing, and audit on one runtime foundation. Every company can run its own Agents with natural language.'
          ) }}
        </p>

        <div class="hero-ctas">
          <a class="btn-primary" :href="withBase(t('/zh/docs/what', '/docs/what'))">
            {{ t('了解 LytgOS2', 'Explore LytgOS2') }}
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 7h10M8 3l4 4-4 4"/></svg>
          </a>
          <a class="btn-ghost" href="https://github.com/luyitiger6/lytgos2" target="_blank" rel="noopener">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.477 2 2 6.477 2 12c0 4.418 2.865 8.166 6.839 9.489.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.745 0 .268.18.58.688.482A10.019 10.019 0 0022 12c0-5.523-4.477-10-10-10z"/></svg>
            GitHub
          </a>
        </div>

        <!-- Terminal Window -->
        <div class="terminal">
          <div class="terminal-titlebar">
            <span class="dot dot-red"></span>
            <span class="dot dot-yellow"></span>
            <span class="dot dot-green"></span>
            <span class="terminal-title">lytgos — bash</span>
          </div>
          <div class="terminal-body">
            <div class="term-line">
              <span class="term-prompt">❯</span>
            <span class="term-cmd">lytgos chat --profile competitive-weekly</span>
            </div>
            <div class="term-output">✓ Session started: competitive-weekly</div>
            <div class="term-output dim">  decompose · assign · collaborate · deliver</div>
            <div class="term-spacer"></div>
            <div class="term-output dim">Team: researcher · analyst · writer · scheduler</div>
            <div class="term-output dim">Runtime: private · data stays in your domain</div>
            <div class="term-spacer"></div>
            <div class="term-line">
              <span class="term-user">task</span>
              <span class="term-msg">{{ t('分析本周竞品动态，生成周五汇报', 'Track competitors this week and produce a Friday report') }}</span>
            </div>
            <div class="term-spacer"></div>
            <div class="term-output agent-label">{{ t('[runtime] Agent 团队协作中...', '[runtime] Agent team collaborating...') }}</div>
            <div class="term-output dim">  → Researcher: web + MCP sources</div>
            <div class="term-output dim">  → Analyst: compare and summarize</div>
            <div class="term-output dim">  → Writer: draft the report</div>
            <div class="term-spacer"></div>
            <div class="term-output agent-label">{{ t('[audit] 每个 Agent 的行动已记录', '[audit] Every Agent action recorded') }}</div>
            <div class="term-output">{{ t('汇报已交付；每个 Agent 的行动都可追溯。', 'Report delivered; every Agent action is traceable.') }}</div>
            <div class="term-spacer"></div>
            <div class="term-line">
              <span class="term-prompt">❯</span>
              <span class="term-cursor"></span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── STATS BAR ── -->
    <div class="stats-bar">
      <div class="stats-inner">
        <div class="stat">
          <span class="stat-num">05</span>
          <span class="stat-label">{{ t('核心能力', 'core capabilities') }}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat">
          <span class="stat-num">09</span>
          <span class="stat-label">{{ t('Maven 模块', 'Maven modules') }}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat">
          <span class="stat-num">09</span>
          <span class="stat-label">{{ t('内置 Tool', 'built-in Tools') }}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat">
          <span class="stat-num">12</span>
          <span class="stat-label">{{ t('CLI 命令', 'CLI commands') }}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat">
          <span class="stat-num">10</span>
          <span class="stat-label">{{ t('REST 端点', 'REST endpoints') }}</span>
        </div>
      </div>
    </div>

    <!-- ── HOW IT WORKS ── -->
    <section class="section section-dark">
      <div class="section-inner">
        <div class="section-header">
          <span class="section-label">{{ t('整体架构', 'ARCHITECTURE') }}</span>
          <h2 class="section-h2">{{ t('企业共享同一套 Agent Harness OS。', 'Enterprises run on one shared Agent Harness OS.') }}</h2>
        </div>

        <div class="arch-diagram">
          <img :src="withBase(t('/images/lytgos-overview-zh.svg', '/images/lytgos-overview-en.svg'))" :alt="t('LytgOS2 整体架构', 'LytgOS2 architecture overview')" class="arch-img"/>
        </div>
      </div>
    </section>

    <!-- ── CORE CAPABILITIES ── -->
    <section class="section section-dark">
      <div class="section-inner">
        <div class="section-header">
          <span class="section-label">{{ t('核心能力', 'CORE CAPABILITIES') }}</span>
          <h2 class="section-h2">{{ t('应用负责业务；LytgOS2 负责运行、协同与治理。', 'Applications own the business; LytgOS2 provides runtime, collaboration, and governance.') }}</h2>
        </div>

        <div class="caps-grid">
          <div v-for="cap in capabilities" :key="cap.num" class="cap-card">
            <div class="cap-top">
              <span class="cap-num">{{ cap.num }}</span>
              <h3 class="cap-title">{{ cap.title }}</h3>
              <p class="cap-desc">{{ cap.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── USE CASES ── -->
    <section class="section section-dark section-use-cases">
      <div class="section-inner">
        <div class="section-header">
          <span class="section-label">{{ t('使用场景', 'USE CASES') }}</span>
          <h2 class="section-h2">{{ t('从单个 Agent 到一支 Agent 团队。', 'From a single Agent to an Agent team.') }}</h2>
        </div>

        <div class="cases-grid">
          <div v-for="s in scenarios" :key="s.num" class="case-card">
            <span class="case-num">{{ s.num }}</span>
            <h3 class="case-title">{{ s.title }}</h3>
            <p class="case-desc">{{ s.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ── ROADMAP ── -->
    <section class="section section-dark section-roadmap">
      <div class="section-inner">
        <div class="section-header">
          <span class="section-label">{{ t('路线图', 'ROADMAP') }}</span>
          <h2 class="section-h2">{{ t('先把单机运行时内核做扎实，再走向分布式协作。', 'Make the single-node runtime kernel solid first, then grow into distributed collaboration.') }}</h2>
          <p class="how-sub">{{ t('多租户、SSO、完整审计、工具策略、可观测、Web 管理作为横向能力，随各阶段逐步补齐。', 'Multi-tenancy, SSO, complete audit, tool policy, observability, and web admin are horizontal capabilities added across phases.') }}</p>
        </div>

        <div class="roadmap-grid">
          <div v-for="p in roadmapPhases" :key="p.phase" class="roadmap-card" :class="{ 'roadmap-card--active': p.active }">
            <div class="roadmap-top">
              <span class="roadmap-phase">{{ p.phase }}</span>
              <span class="roadmap-status" :class="{ 'roadmap-status--active': p.active }">{{ p.status }}</span>
            </div>
            <h3 class="roadmap-title">{{ p.title }}</h3>
            <ul class="roadmap-items">
              <li v-for="item in p.items" :key="item" class="roadmap-item">{{ item }}</li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- ── CTA ── -->
    <section class="section section-cta">
      <div class="section-inner">
        <div class="cta-grid">
          <div class="cta-left">
            <span class="section-label label-dark">{{ t('立即开始', 'GET STARTED') }}</span>
            <h2 class="cta-h2">{{ t('用一句自然语言，跑起来你自己的 Agent。', 'Run your own Agents with one sentence.') }}</h2>
            <p class="cta-sub">{{ t('从一个 Agent 目录开始，接入模型、Tool、记忆和 MCP；私有部署，数据不出域，运行过程全程可审计。', 'Start with one Agent directory, connect models, tools, memory, and MCP; deploy privately, keep data in your domain, and audit every action.') }}</p>
            <div class="cta-btns">
              <a class="btn-dark" :href="withBase(t('/zh/docs/what', '/docs/what'))">{{ t('查看文档', 'Read the Docs') }}</a>
              <a class="btn-dark-ghost" href="https://github.com/luyitiger6/lytgos2" target="_blank" rel="noopener">GitHub</a>
            </div>
          </div>
          <div class="cta-right">
            <div class="cta-terminal">
              <div class="cta-terminal-bar">
                <span class="dot dot-dark"></span>
                <span class="dot dot-dark"></span>
                <span class="dot dot-dark"></span>
              </div>
              <pre class="cta-code"><code><span class="code-comment"># 1. {{ t('构建并启动', 'Build and start') }}</span>
<span class="code-prompt">❯</span> mvn clean package
<span class="code-prompt">❯</span> java -jar lytgos-boot/target/lytgos-boot-*.jar serve

<span class="code-comment"># 2. {{ t('定义一个 Agent', 'Define an Agent') }}</span>
<span class="code-prompt">❯</span> .lytgos/agents/ops-agent/AGENT.md

<span class="code-comment"># 3. {{ t('发布一个任务', 'Publish a task') }}</span>
<span class="code-prompt">❯</span> {{ t('分析竞品动态，生成周五汇报', "Analyze competitors; deliver Friday's report") }}</code></pre>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── FOOTER ── -->
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="footer-logo">Lytg<strong>OS2</strong></span>
          <span class="footer-tagline">{{ t('企业 Agent 操作系统 · 私有部署 · 数据不出域', 'Enterprise Agent Operating System · Self-hosted · Data stays in your domain') }}</span>
        </div>
        <div class="footer-links">
          <a :href="withBase(t('/zh/docs/what', '/docs/what'))" class="footer-link">{{ t('文档', 'Docs') }}</a>
          <a :href="withBase(t('/zh/labs', '/labs'))" class="footer-link">Lytg-labs</a>
          <a href="https://github.com/luyitiger6/lytgos2" target="_blank" rel="noopener" class="footer-link">GitHub</a>
        </div>
      </div>
    </footer>

  </div>
</template>

<style scoped>
/* ────────────────────────────────────────────────
   RESET / BASE
──────────────────────────────────────────────── */
.home {
  min-height: 100vh;
  background: #000000;
  color: #fafafa;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  /* Override VitePress default page padding */
  margin: 0;
  padding: 0;
}
.home * { box-sizing: border-box; }
a { text-decoration: none; }

/* ────────────────────────────────────────────────
   HERO
──────────────────────────────────────────────── */
.hero {
  background: #000000;
  padding: 96px 24px 80px;
  text-align: center;
}
.hero-inner {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-eyebrow {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  color: #888888;
  margin: 0 0 32px;
  letter-spacing: 0.02em;
}
.eyebrow-comment { color: #38bdf8; }

.hero-headline {
  font-size: clamp(40px, 7vw, 72px);
  font-weight: 900;
  line-height: 1.05;
  letter-spacing: -0.03em;
  margin: 0 0 28px;
}
.headline-tag {
  display: inline-block;
  font-size: 0.38em;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.5);
  border-radius: 4px;
  padding: 3px 10px;
  margin-bottom: 12px;
  vertical-align: middle;
}
.headline-white { color: #fafafa; }
.headline-amber { color: #38bdf8; }

.hero-sub {
  font-size: 16px;
  line-height: 1.75;
  color: #888888;
  max-width: 620px;
  margin: 0 0 40px;
}

/* CTA Buttons */
.hero-ctas {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 56px;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  border-radius: 6px;
  background: #38bdf8;
  color: #000000;
  font-weight: 700;
  font-size: 14px;
  letter-spacing: 0.01em;
  transition: background 0.15s, transform 0.15s;
}
.btn-primary:hover { background: #7dd3fc; transform: translateY(-1px); }
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 6px;
  border: 1px solid #333333;
  color: #fafafa;
  font-weight: 600;
  font-size: 14px;
  transition: border-color 0.15s, color 0.15s;
}
.btn-ghost:hover { border-color: #38bdf8; color: #38bdf8; }

/* Terminal */
.terminal {
  width: 100%;
  max-width: 680px;
  border-radius: 10px;
  border: 1px solid #1e1e1e;
  background: #0a0a0a;
  overflow: hidden;
  text-align: left;
  box-shadow: 0 32px 80px rgba(0,0,0,0.8), 0 0 0 1px #1e1e1e;
}
.terminal-titlebar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #111111;
  border-bottom: 1px solid #1e1e1e;
}
.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-red    { background: #ff5f57; }
.dot-yellow { background: #febc2e; }
.dot-green  { background: #28c840; }
.terminal-title {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #444444;
  margin-left: 8px;
}
.terminal-body {
  padding: 20px 20px 24px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.7;
}
.term-line { display: flex; align-items: baseline; gap: 8px; }
.term-prompt { color: #38bdf8; font-weight: 700; }
.term-cmd { color: #fafafa; }
.term-output { color: #d4d4d4; padding-left: 0; }
.term-output.dim { color: #555555; }
.term-spacer { height: 6px; }
.term-user {
  color: #22c55e;
  font-weight: 700;
  flex-shrink: 0;
}
.term-msg { color: #fafafa; }
.agent-label { color: #38bdf8; font-weight: 700; }
.term-cursor {
  display: inline-block;
  width: 8px;
  height: 14px;
  background: #38bdf8;
  animation: blink 1.2s step-end infinite;
  vertical-align: text-bottom;
  margin-left: 2px;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0; }
}

/* ────────────────────────────────────────────────
   STATS BAR
──────────────────────────────────────────────── */
.stats-bar {
  background: #0d0d0d;
  border-top: 1px solid #1e1e1e;
  border-bottom: 1px solid #1e1e1e;
  padding: 0 24px;
}
.stats-inner {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 0;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
}
.stat-num {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 28px;
  font-weight: 900;
  color: #38bdf8;
  line-height: 1;
}
.stat-label {
  font-size: 11px;
  color: #555555;
  text-align: center;
  letter-spacing: 0.03em;
}
.stat-divider {
  width: 1px;
  height: 40px;
  background: #1e1e1e;
  flex-shrink: 0;
}

/* ────────────────────────────────────────────────
   SECTIONS BASE
──────────────────────────────────────────────── */
.section { padding: 88px 24px; }
.section-inner { max-width: 1040px; margin: 0 auto; }
.section-dark { background: #000000; }
.section-light { background: #fafafa; }
.section-use-cases { border-top: 1px solid #1e1e1e; }

.section-header {
  text-align: center;
  margin-bottom: 56px;
}
.section-label {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: #38bdf8;
  display: block;
  margin-bottom: 16px;
}
.label-dark { color: #000000; }
.section-h2 {
  font-size: clamp(26px, 4vw, 42px);
  font-weight: 800;
  color: #fafafa;
  margin: 0;
  letter-spacing: -0.02em;
  line-height: 1.1;
}
.section-h2.dark { color: #0a0a0a; }

/* ────────────────────────────────────────────────
   HOW IT WORKS — FLOW
──────────────────────────────────────────────── */
.flow {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 40px;
  flex-wrap: nowrap;
  overflow-x: auto;
}
.flow-col {
  position: relative;
  flex: 1;
  min-width: 160px;
  max-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.flow-col-label {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #888888;
  text-align: center;
  margin-bottom: 4px;
}
.flow-col--highlight .flow-col-label { color: #38bdf8; }
.flow-nodes {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.flow-node {
  padding: 10px 14px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #111111;
  font-size: 12px;
  text-align: center;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  line-height: 1.4;
}
.flow-col--highlight .flow-node {
  border-color: #38bdf8;
  background: rgba(56,189,248,0.06);
  color: #1a1a1a;
}
.flow-col:not(:last-child)::after {
  content: '→';
  position: absolute;
  right: -22px;
  top: 48px;
  font-size: 20px;
  color: #9ca3af;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

/* ────────────────────────────────────────────────
   CORE CAPABILITIES
──────────────────────────────────────────────── */
.caps-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: #1e1e1e;
  border: 1px solid #1e1e1e;
  border-radius: 12px;
  overflow: hidden;
}
.cap-card {
  background: #111111;
  padding: 32px 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  transition: background 0.2s;
  cursor: default;
}
.cap-card:hover {
  background: #161616;
  box-shadow: inset 0 0 0 1px #38bdf8;
}
.cap-top { display: flex; flex-direction: column; gap: 10px; }
.cap-num {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 700;
  color: #38bdf8;
  letter-spacing: 0.1em;
}
.cap-title {
  font-size: 18px;
  font-weight: 700;
  color: #fafafa;
  margin: 0;
  line-height: 1.2;
}
.cap-desc {
  font-size: 13px;
  color: #888888;
  line-height: 1.7;
  margin: 0;
}
.cap-code {
  background: #0a0a0a;
  border: 1px solid #1e1e1e;
  border-radius: 6px;
  padding: 16px;
  font-size: 12px;
  line-height: 1.65;
  color: #d4d4d4;
  overflow-x: auto;
  margin: 0;
  white-space: pre;
  flex: 1;
}
.cap-code code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  background: none;
  color: inherit;
}

/* ────────────────────────────────────────────────
   USE CASES
──────────────────────────────────────────────── */
.cases-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: #1e1e1e;
  border: 1px solid #1e1e1e;
  border-radius: 12px;
  overflow: hidden;
}
.case-card {
  background: #111111;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-left: 3px solid transparent;
  transition: border-color 0.2s, background 0.2s;
  cursor: default;
}
.case-card:hover {
  border-left-color: #38bdf8;
  background: #141414;
}
.case-num {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: #444444;
  font-weight: 700;
  align-self: flex-end;
}
.case-title {
  font-size: 15px;
  font-weight: 700;
  color: #fafafa;
  margin: 0;
}
.case-desc {
  font-size: 12px;
  color: #666666;
  line-height: 1.65;
  margin: 0;
}

/* ────────────────────────────────────────────────
   CTA
──────────────────────────────────────────────── */
.section-cta {
  background: #38bdf8;
  padding: 88px 24px;
}
.cta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 64px;
  align-items: center;
  max-width: 1040px;
  margin: 0 auto;
}
.cta-h2 {
  font-size: clamp(28px, 4vw, 48px);
  font-weight: 900;
  color: #000000;
  margin: 12px 0 16px;
  letter-spacing: -0.03em;
  line-height: 1.05;
}
.cta-sub {
  font-size: 15px;
  color: rgba(0,0,0,0.65);
  line-height: 1.7;
  margin: 0 0 32px;
}
.cta-btns { display: flex; gap: 12px; flex-wrap: wrap; }
.btn-dark {
  display: inline-flex;
  align-items: center;
  padding: 12px 24px;
  border-radius: 6px;
  background: #000000;
  color: #fafafa;
  font-weight: 700;
  font-size: 14px;
  transition: background 0.15s;
}
.btn-dark:hover { background: #111111; }
.btn-dark-ghost {
  display: inline-flex;
  align-items: center;
  padding: 12px 24px;
  border-radius: 6px;
  border: 2px solid rgba(0,0,0,0.25);
  color: #000000;
  font-weight: 700;
  font-size: 14px;
  transition: border-color 0.15s;
}
.btn-dark-ghost:hover { border-color: #000000; }
.cta-terminal {
  border-radius: 10px;
  border: 1px solid rgba(0,0,0,0.15);
  background: #0a0a0a;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0,0,0,0.4);
}
.cta-terminal-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #111111;
  border-bottom: 1px solid #1e1e1e;
}
.dot-dark { background: #333333; }
.cta-code {
  padding: 24px 20px;
  font-size: 13px;
  line-height: 1.75;
  margin: 0;
  white-space: pre;
  overflow-x: auto;
}
.cta-code code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  background: none;
  color: #d4d4d4;
}
.code-comment { color: #555555; }
.code-prompt { color: #38bdf8; font-weight: 700; }

/* ────────────────────────────────────────────────
   FOOTER
──────────────────────────────────────────────── */
.footer {
  background: #000000;
  border-top: 1px solid #111111;
  padding: 32px 24px;
}
.footer-inner {
  max-width: 1040px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.footer-brand { display: flex; flex-direction: column; gap: 4px; }
.footer-logo {
  font-size: 18px;
  font-weight: 400;
  color: #fafafa;
  letter-spacing: -0.01em;
}
.footer-logo strong { font-weight: 900; }
.footer-tagline {
  font-size: 12px;
  color: #444444;
}
.footer-links { display: flex; gap: 24px; }
.footer-link {
  font-size: 13px;
  color: #555555;
  transition: color 0.15s;
}
.footer-link:hover { color: #38bdf8; }

/* ────────────────────────────────────────────────
   RESPONSIVE
──────────────────────────────────────────────── */
@media (max-width: 900px) {
  .caps-grid { grid-template-columns: 1fr; }
  .cases-grid { grid-template-columns: repeat(2, 1fr); }
  .cta-grid { grid-template-columns: 1fr; gap: 40px; }
}

@media (max-width: 768px) {
  .hero { padding: 72px 20px 64px; }
  .hero-headline { font-size: clamp(36px, 10vw, 56px); }
  .section { padding: 64px 20px; }
  .stats-inner { flex-wrap: wrap; gap: 24px; justify-content: center; }
  .stat-divider { display: none; }
  .stat { flex: none; width: 80px; }
  .flow { gap: 0; overflow-x: auto; }
  .flow-col { min-width: 130px; }
  .caps-grid { grid-template-columns: 1fr; }
  .cases-grid { grid-template-columns: 1fr; }
  .cta-grid { grid-template-columns: 1fr; }
  .footer-inner { flex-direction: column; gap: 20px; text-align: center; }
  .footer-links { justify-content: center; }
}

@media (max-width: 480px) {
  .hero-ctas { flex-direction: column; align-items: center; }
  .btn-primary, .btn-ghost { width: 200px; justify-content: center; }
}

/* ────────────────────────────────────────────────
   HOW IT WORKS — SUB PARAGRAPH
──────────────────────────────────────────────── */
.how-sub {
  font-size: 14px;
  line-height: 1.75;
  color: #666666;
  text-align: center;
  max-width: 680px;
  margin: -32px auto 48px;
}
.arch-diagram {
  width: 100%;
  margin-top: 8px;
  overflow-x: auto;
}
.arch-img {
  display: block;
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  border-radius: 10px;
}

/* ────────────────────────────────────────────────
   ROADMAP
──────────────────────────────────────────────── */
.section-roadmap { border-top: 1px solid #1e1e1e; }

.roadmap-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: #1e1e1e;
  border: 1px solid #1e1e1e;
  border-radius: 12px;
  overflow: hidden;
}

.roadmap-card {
  background: #111111;
  padding: 32px 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border-left: 3px solid transparent;
  transition: background 0.2s;
}

.roadmap-card--active {
  border-left-color: #38bdf8;
  background: #141414;
}

.roadmap-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.roadmap-phase {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 700;
  color: #38bdf8;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.roadmap-status {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #444444;
  padding: 3px 8px;
  border: 1px solid #333333;
  border-radius: 4px;
}

.roadmap-status--active {
  color: #38bdf8;
  border-color: #38bdf8;
  background: rgba(56, 189, 248, 0.08);
}

.roadmap-title {
  font-size: 17px;
  font-weight: 700;
  color: #fafafa;
  margin: 0;
  line-height: 1.25;
}

.roadmap-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.roadmap-item {
  font-size: 13px;
  color: #666666;
  line-height: 1.5;
  padding-left: 16px;
  position: relative;
}

.roadmap-item::before {
  content: '—';
  position: absolute;
  left: 0;
  color: #333333;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

@media (max-width: 900px) {
  .roadmap-grid { grid-template-columns: 1fr; }
}
</style>
