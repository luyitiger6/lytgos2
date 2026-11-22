# Provider

LytgOS2 通过 Spring AI Alibaba `ChatModel` 之上的一层薄封装 `ProviderService` 对接 LLM。该服务对 ReAct 循环屏蔽厂商差异，并记录每一次调用。1.0 基线保持 Spring AI Alibaba 1.1.2.x + Spring AI 1.1.2。

## 显式 name → ChatModel 映射

配置多个 Provider 时，Spring 容器里会有多个同类型的 `ChatModel` Bean。按类型扫描无法可靠区分哪个是 DeepSeek、哪个是 Kimi。LytgOS2 维护一份显式的 **provider name → `ChatModel`** 映射，在启动时根据配置建立。Agent 在 `AGENT.md` 中按名称引用 Provider，不按类型选择 Bean。

## 配置

Provider 凭证放在不提交到仓库的外部配置文件中，使用环境变量占位符：

```yaml
lytgos:
  providers:
    - name: deepseek
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
    - name: kimi
      api-key: ${MOONSHOT_API_KEY}
      base-url: https://api.moonshot.cn/v1
```

- 1.0 至少跑通 DeepSeek 和 Kimi；其他 OpenAI 兼容 Provider 可以通过配置扩展。
- 不要把明文密钥写进 `AGENT.md`、`application.yml` 或仓库。
- 配置缺失或非法时，启动阶段给出清晰报错。

Agent 按名称引用 Provider：

```yaml
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
```

API key 从 Provider 配置解析，不写进 `AGENT.md`。

## 审计

每次调用写入 `llm_calls`：

- provider name 与 model
- prompt / completion / total tokens
- 耗时
- 成功或错误

## 1.0 边界

Provider fallback、hedge racing、circuit breaker、自适应路由、运行时可变的 Provider 注册表、Provider CRUD API 都不属于 1.0。Provider 列表是配置，不是运行时功能。

使用 `lytgos provider list` 查看已配置的 Provider。
