# Contract: Profile 与全局 Provider 配置

## 1. 全局 Provider 配置

位置：`lytgos-boot/src/main/resources/application.yaml` 的 `lytgos.providers`。

```yaml
lytgos:
  workspace: .lytgos
  providers:
    - name: deepseek
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:}
    - name: kimi
      base-url: https://api.moonshot.cn/v1
      api-key: ${KIMI_API_KEY:}
```

规则：

- `name` 非空且在同一配置中唯一；重复名称记录错误，保留首个。
- `base-url` 非空；用于 OpenAI 兼容协议连接。
- `api-key` 使用 `${ENV_VAR}` 占位符，不写明文；为空时不构造该 provider 的模型，应用仍可启动。
- Profile 引用全局不存在的 provider 时，加载阶段报错。
- Profile 引用的 provider 存在但模型未构造（如缺少 key）时，调用前报错，不静默回退。

## 2. Profile 配置

位置：`${lytgos.workspace}/profiles/*.yaml`，本节默认 `.lytgos/profiles/`。

```yaml
name: ops-agent
description: 运维助手
identity:
  agent_name: 运维小助手
  prompt: 你负责回答运维相关问题
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
tools:
  - http_get
skills: []
mcp_servers: []
channels: []
notify_channels: []
schedules: []
bootstrap: []
settings:
  max_iterations: 10
  max_history_turns: 20
```

### 2.1 字段规则

- `name`、`provider.name`、`provider.model` 必填。
- 列表字段缺省为空列表；`temperature` 缺省为 0.7；`settings` 缺省为 `max_iterations=10`、`max_history_turns=20`。
- `notify_channels` 是课件兼容字段，本节只解析、不消费；第 19 节迁移到全局通知渠道注册表。
- 字符串值中的 `${ENV_VAR}` 占位符由加载器解析；测试可注入环境变量解析器。

### 2.2 加载行为

- 目录不存在：返回空 Profile 列表，不报错。
- 单个 YAML 损坏或校验失败：记录错误日志，跳过该文件，继续加载其他文件。
- Profile 名称重复：保留首个，记录错误。
- 合法 Profile 进入 `ProfileRegistry` 的内存索引，按 `name` 查找。

## 3. 示例调用路径

1. 应用启动，从 `lytgos.providers` 构造显式 provider → `ChatModel` 映射。
2. `ProfileLoader` 扫描 `.lytgos/profiles/*.yaml`，校验 provider 名称，注册到 `ProfileRegistry`。
3. 调用方按名称取 Profile，构造包含消息和可选工具回调的 Spring AI `Prompt`。
4. `ProviderService.chat(sessionId, profile, prompt)` 选择模型、关闭自动工具执行、调用一次、写审计。
