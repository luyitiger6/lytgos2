# LytgOS2 Website

LytgOS2 的官方网站与文档站，基于 VitePress 1.6 + Vue 3。

站点默认英文，中文位于 `/zh/`，通过导航栏的语言切换进入。

## 本地开发

```bash
cd website
npm install
npm run docs:dev
```

默认地址：`http://localhost:5173/`

## 本地假域名 `lytiger.lytgos2.com`

生产域名是 `https://lytiger.lytgos2.com/`。本地通过 hosts 映射模拟该域名。

以管理员身份打开 PowerShell，执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\add-hosts.ps1
```

或者手动追加 hosts 记录：

```powershell
Add-Content -Path "$env:SystemRoot\System32\drivers\etc\hosts" -Value "127.0.0.1 lytiger.lytgos2.com"
```

然后二选一：

```bash
# 普通权限，带端口访问
npm run docs:dev
# http://lytiger.lytgos2.com:5173/

# 管理员权限，直接使用 80 端口
npm run docs:dev:domain
# http://lytiger.lytgos2.com/
```

VitePress 配置中已加入 `allowedHosts: ['lytiger.lytgos2.com', ...]`，自定义域名不会被 Vite 拦截。

## 构建与预览

```bash
npm run docs:build
npm run docs:preview
# http://localhost:4173/
```

构建产物位于 `.vitepress/dist/`。

## 目录结构

```text
website/
├── .vitepress/
│   ├── config.mts              # 站点标题、语言、导航、侧边栏、域名、Vite 配置
│   └── theme/
│       ├── index.ts            # 注册 Home 组件与自定义 Layout
│       ├── custom.css          # 黑底 + 蓝紫品牌色、导航/侧边栏/代码块样式
│       └── components/
│           ├── Home.vue        # 整个首页（Hero、终端、统计、架构、能力、场景、路线图、CTA）
│           └── Layout.vue      # 继承默认主题
├── docs/                       # 英文文档
├── zh/docs/                    # 中文文档
├── public/
│   ├── logo.svg                # 横向品牌字标
│   ├── favicon.svg             # 站点图标
│   └── images/                 # 首页与文档引用的架构图、ReAct 循环图、GitHub 流程图
├── index.md                    # 英文首页入口（<Home />）
└── zh/index.md                 # 中文首页入口（<Home />）
```

## 品牌

- 主强调色：`#38bdf8`（sky）
- 次级色：`#0ea5e9` / `#0284c7`
- 渐变与辅助色：`#818cf8`（indigo）、`#a78bfa`（violet）、`#34d399`（emerald）
- 背景保持参考站的纯黑 `#000000` / 深灰 `#0a0a0a` / `#111111`
- Logo 与架构图由 `docs/images/logo.svg` 和 `docs/images/architecture.svg` 的视觉语言重绘

## 内容说明

首页文案已按 `docs/lytgos.md` 的新定位重写：企业 Agent 操作系统（Agent Harness OS）、一句自然语言 → Agent 团队协作、五大核心能力、三阶段路线图、私有部署与全链路审计。统计条显示：5 大核心能力、9 个 Maven 模块、9 个内置 Tool、12 个 CLI 命令、10 个核心 REST 端点。

产品名保持 **LytgOS2**，CLI 为 `lytgos`，工作区为 `.lytgos/`，Maven 模块为 `lytgos-*`，仓库地址为 `https://github.com/luyitiger6/lytgos2`。许可证为 Apache 2.0。

`docs/` 下的技术文档（架构、ReAct、Provider、Memory、Tool、API、CLI、Profile、认证、Quick Start、贡献指南等）已按 `docs/ReleaseBaseline.md` 的 1.0 范围对齐；后续实现推进时继续同步。

导航中的 **Lytg-labs** 目前指向建设中页面（`/labs` 与 `/zh/labs`），等社区站点或入口准备好后再替换。
