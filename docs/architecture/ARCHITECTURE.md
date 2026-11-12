# 系统架构

## 1. 总体设计

```text
Web UI / API Client
        |
Identity + Project ACL
        |
Agent Harness
  | Scope Resolver
  | Query Planner
  | Tool Router
  | RunState / Budget / Trace
  | Citation Verifier
        |
Knowledge Retrieval Service
  | BM25
  | Hash Vector
  | Weighted Fusion
  | Rerank
        |
In-Memory Knowledge Store
  | Project / Document / Section / Decision
```

系统采用“显式 Harness + 分层知识 + 检索前 ACL”的结构。LLM 只负责查询改写、回答组织和不确定性表达，不能自行决定权限，也不能绕过证据直接回答。

## 2. 知识分层

- **L0 摘要**：文档的一句话摘要，用于快速判断相关性。
- **L1 概览**：文档标题、标签、章节目录和摘要，用于规划和导航。
- **L2 原文**：章节正文，只将命中的必要内容加入 Agent 上下文。

摘要和原文使用相同版本身份。摘要不能作为事实唯一来源，最终引用必须落到 L2 章节。

## 3. 摄入流程

```text
文档提交 -> 项目权限校验 -> Markdown 标题解析 -> 章节切分
        -> L0/L1 元数据 -> 分词与向量化 -> 写入索引 -> 可检索
```

当前版本使用确定性摘要和内存索引，保证比赛现场无需外部服务即可运行。

## 4. 问答流程

```text
Question -> 身份识别 -> 作用域解析 -> 查询意图分类
         -> 混合检索与 Rerank -> Evidence Pack -> Answer
         -> Citation Verifier -> Answer / Abstain -> Trace
```

Harness 的每次运行保存问题、用户、项目范围、查询计划、证据、回答、状态和耗时。

## 5. 检索与记忆

BM25 负责精确词项，哈希向量负责轻量语义召回，融合后再对标题、章节和项目名称重排。知识、会话记忆和项目记忆严格分离：项目事实只能来自权威文档，Session Memory 只用于连续追问。

设计参考 OpenViking 的目录范围检索、L0/L1/L2 渐进加载，以及 TencentDB Agent Memory 的分层事实、固定绑定 ACL、混合召回和按需工具调用，但不直接复制实现。

## 6. 权限与可靠性

- 服务端根据 `X-User-Id` 计算可见项目。
- 指定未授权项目时直接返回 `FORBIDDEN`。
- 未指定项目时只在可见项目集合中检索。
- 每次运行生成 runId 和阶段 Trace。
- 无证据返回 `ABSTAINED`，不生成引用。
- 引用校验会移除不存在的 evidence ID。
