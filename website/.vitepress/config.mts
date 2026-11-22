import { defineConfig } from 'vitepress'

const base = '/'
const siteUrl = 'https://lytiger.lytgos2.com/'

export default defineConfig({
  title: 'LytgOS2',
  titleTemplate: ':title — LytgOS2',
  description: 'LytgOS2 is the self-hosted enterprise Agent Operating System (Agent Harness OS) — one natural-language task, one Agent team, private deployment, full audit',
  base,
  cleanUrls: true,
  srcExclude: ['README.md'],
  appearance: 'force-light',

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}favicon.svg` }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    ['link', { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@700&display=swap' }],
    ['meta', { name: 'author', content: 'LytgOS2' }],
    ['meta', { name: 'keywords', content: 'LytgOS2, Agent OS, Agent Harness OS, Agent Runtime, multi-Agent, enterprise AI, natural language task, private deployment, MCP, A2A, audit, self-hosted AI' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'LytgOS2' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
  ],

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/' },
          { text: 'Docs', link: '/docs/what' },
          { text: 'Contribute', link: '/docs/contributing' },
          { text: 'Lytg-labs', link: '/labs' },
          { text: 'GitHub', link: 'https://github.com/luyitiger6/lytgos2' },
        ],
        sidebar: {
          '/docs/': [
            {
              text: 'Introduction',
              items: [
                { text: 'What is LytgOS2', link: '/docs/what' },
                { text: 'Why LytgOS2', link: '/docs/why' },
              ],
            },
            {
              text: 'Getting Started',
              items: [
                { text: 'Quick Start', link: '/docs/quick-start' },
              ],
            },
            {
              text: 'Architecture',
              items: [
                { text: 'Overview', link: '/docs/architecture' },
                { text: 'ReAct Loop', link: '/docs/react-loop' },
                { text: 'Provider', link: '/docs/provider' },
                { text: 'Memory', link: '/docs/memory' },
                { text: 'Tool System', link: '/docs/tool' },
              ],
            },
            {
              text: 'Reference',
              items: [
                { text: 'REST API', link: '/docs/api' },
                { text: 'CLI', link: '/docs/cli' },
                { text: 'Profile YAML', link: '/docs/profile' },
                { text: 'Auth', link: '/docs/auth' },
                { text: 'Roadmap', link: '/docs/roadmap' },
              ],
            },
            {
              text: 'Community',
              items: [
                { text: 'Contributing Guide', link: '/docs/contributing' },
                { text: 'Maintainer Guide', link: '/docs/maintainer' },
                { text: 'GitHub Contribution Basics', link: '/docs/github-workflow' },
              ],
            },
          ],
        },
      },
    },
    zh: {
      label: '中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '首页', link: '/zh/' },
          { text: '文档', link: '/zh/docs/what' },
          { text: '贡献', link: '/zh/docs/contributing' },
          { text: 'Lytg-labs', link: '/zh/labs' },
          { text: 'GitHub', link: 'https://github.com/luyitiger6/lytgos2' },
        ],
        sidebar: {
          '/zh/docs/': [
            {
              text: '介绍',
              items: [
                { text: 'LytgOS2 是什么', link: '/zh/docs/what' },
                { text: '为什么需要 LytgOS2', link: '/zh/docs/why' },
              ],
            },
            {
              text: '快速入门',
              items: [
                { text: '快速开始', link: '/zh/docs/quick-start' },
              ],
            },
            {
              text: '架构设计',
              items: [
                { text: '架构概览', link: '/zh/docs/architecture' },
                { text: 'ReAct 循环', link: '/zh/docs/react-loop' },
                { text: 'Provider 路由', link: '/zh/docs/provider' },
                { text: '记忆系统', link: '/zh/docs/memory' },
                { text: 'Tool 体系', link: '/zh/docs/tool' },
              ],
            },
            {
              text: '参考',
              items: [
                { text: 'REST API', link: '/zh/docs/api' },
                { text: 'CLI 命令', link: '/zh/docs/cli' },
                { text: 'Profile YAML', link: '/zh/docs/profile' },
                { text: '认证', link: '/zh/docs/auth' },
                { text: '路线图', link: '/zh/docs/roadmap' },
              ],
            },
            {
              text: '社区',
              items: [
                { text: '贡献指南', link: '/zh/docs/contributing' },
                { text: 'Maintainer 指南', link: '/zh/docs/maintainer' },
                { text: 'GitHub 贡献入门', link: '/zh/docs/github-workflow' },
              ],
            },
          ],
        },
      },
    },
  },

  themeConfig: {
    siteTitle: false,
    logo: '/logo.svg',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/luyitiger6/lytgos2' },
    ],
  },

  vite: {
    server: {
      host: '0.0.0.0',
      allowedHosts: ['lytiger.lytgos2.com', 'localhost', '127.0.0.1'],
    },
    preview: {
      host: '0.0.0.0',
      allowedHosts: ['lytiger.lytgos2.com', 'localhost', '127.0.0.1'],
    },
  },

  sitemap: {
    hostname: siteUrl,
  },

  transformHead({ page, title, description }) {
    const pagePath = page.replace(/index\.md$/, '').replace(/\.md$/, '')
    const canonicalUrl = new URL(pagePath || './', siteUrl).href

    return [
      ['meta', { property: 'og:title', content: title }],
      ['meta', { property: 'og:description', content: description }],
      ['meta', { property: 'og:url', content: canonicalUrl }],
      ['meta', { name: 'twitter:title', content: title }],
      ['meta', { name: 'twitter:description', content: description }],
      ['link', { rel: 'canonical', href: canonicalUrl }],
    ]
  },
})
