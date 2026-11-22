# Provider

LytgOS2 talks to LLMs through a thin `ProviderService` on top of Spring AI Alibaba's `ChatModel`. The service hides vendor differences from the ReAct loop and records every call. The 1.0 baseline keeps Spring AI Alibaba 1.1.2.x with Spring AI 1.1.2.

## Explicit name → ChatModel mapping

When several providers are configured, the Spring container contains several `ChatModel` beans of the same type. Scanning by type cannot reliably tell which bean is DeepSeek and which is Kimi. LytgOS2 keeps an explicit **provider name → `ChatModel`** map, built at startup from configuration. An Agent references a provider by name in `AGENT.md`; it never selects a bean by type.

## Configuration

Provider credentials live in an external config file (not committed), using environment placeholders:

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

- 1.0 must work with DeepSeek and Kimi. Other OpenAI-compatible providers can be added by configuration.
- Never put a raw key in `AGENT.md`, `application.yml`, or the repository.
- Missing or invalid configuration fails at startup with a clear error.

An Agent references a provider by name:

```yaml
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
```

The API key is resolved from the provider configuration, not from `AGENT.md`.

## Audit

Every call writes a row to `llm_calls`:

- provider name and model
- prompt / completion / total tokens
- duration
- success or error

## 1.0 boundaries

Provider fallback, hedge racing, circuit breakers, adaptive routing, a runtime-mutable provider registry, and provider CRUD APIs are not part of 1.0. The provider list is configuration, not a runtime feature.

Use `lytgos provider list` to print the configured providers.
