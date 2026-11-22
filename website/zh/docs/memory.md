# 记忆系统

`MemoryService` 是唯一的内存门面，隐藏两层记忆：

- **会话记忆**——某个 Session 的对话历史，存储在 SQLite。
- **长期记忆**——持久化笔记，存储在 `MEMORY.md`。

## 会话记忆

每个 Session 在 `sessions` 表中保存元数据和 JSON 对话历史。`AgentService` 在处理后追加消息并持久化 Session；重启不会丢历史。注入 prompt 的最近轮次不超过 `max_history_turns`（默认 20）。HTTP API 每个 Session 最多返回最近 100 条消息。

## 长期记忆

1.0 只有一个长期记忆后端：`MarkdownMemoryStore`。

```text
.lytgos/memory/MEMORY.md
```

文件有两个一级分区：

```md
## 核心记忆
- 周报每周五 17:00 发出。

## 归档记忆
- 2026-11：发布窗口为周四晚间。
```

- **核心记忆**——全量注入 system prompt，永不截断，不参与检索。
- **归档记忆**——可以截断，支持关键词检索。

Agent 通过 `save_memory` Tool 显式写入记忆：

```json
{"content": "周报每周五 17:00 发出", "scope": "core"}
```

通过 `recall_memory` 检索归档记忆：

```json
{"query": "周报时间"}
```

1.0 不做自动事实抽取。由 Agent 自己决定记住什么。

## USER.md 与 MEMORY.md

| 文件 | 谁写 | 用途 |
| --- | --- | --- |
| `USER.md` | 人 | 用户偏好和背景。从 LytgOS2 的视角是只读的。 |
| `MEMORY.md` | Agent 通过 `save_memory` 写 | Agent 自己的成长记忆。 |

两者都会注入 system prompt。`USER.md` 由用户维护，`MEMORY.md` 由 Agent 写入。

## 边界

1.0 不包含向量检索、语义召回、自动记忆抽取、情景记忆、Memory Wiki，也不包含 SQLite/Mem0 后端。`LongTermMemoryStore` 是这些后端的扩展点。1.0 只实现 Markdown 后端。
