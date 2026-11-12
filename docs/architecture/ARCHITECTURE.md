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

## 6. 项目记忆

项目记忆不是直接生成的事实。每个记忆必须绑定一次 `AgentRun`、该项目下的 evidenceId 和创建者；初始状态为 `PROPOSED`，项目成员确认后变为 `ACTIVE`，撤回后为 `RETIRED`。已撤回记忆保留审计信息，但不会进入后续上下文。

Harness 会按当前问题和项目范围召回少量 `ACTIVE` 记忆，作为软上下文加入回答提示与 Trace。软上下文不能作为引用来源，事实仍必须来自当前召回的项目文档；候选和撤回记忆不参与 Context Assembly。

## 7. 知识缺口闭环

当 Harness 输出 `ABSTAINED` 时，系统自动创建或累加知识缺口。缺口记录问题、项目范围、来源 runId、重复次数和处理状态。项目成员可在知识缺口面板中标记已解决或忽略。新文档摄入后会与开放缺口做词项匹配；命中的缺口变为 `IN_REVIEW` 并记录 candidateDocumentId。系统不会自动把缺口标记为已解决，必须由项目成员复核。该闭环把“回答不了的问题”转化为下一轮知识建设输入。

## 8. 流式执行

`KnowledgeAgentHarness` 接受阶段事件观察者。同步 API 忽略事件；`/api/agent/ask/stream` 通过 `SseEmitter` 输出 `trace` 事件和最终 `result` 事件。事件顺序覆盖 scope、memory、tool、retrieve、answer、verify、gap。观察者异常不会中断 Agent 主流程。

## 9. 文档版本与时效

文档可通过 `lineageKey` 建立版本谱系。写入更高版本时，旧版本标记为 `SUPERSEDED` 并保留 `supersededBy`；检索层只读取 `ACTIVE` 文档。未显式提供谱系时，系统使用项目与规范化来源 URI 作为谱系键。

## 10. 证据下钻

Evidence 返回 documentId 和 sectionId。前端通过受 ACL 保护的章节详情接口读取完整原文，不允许通过客户端自行拼接未授权文档地址。章节详情同时返回版本、谱系状态、来源和 supersededBy。

## 11. 评测历史

每次运行固定评测后保存 `EvaluationSnapshot`，包括时间、用例数、通过数、通过率、引用覆盖、拒答准确率和权限违规数。历史记录用于比较模型、检索或提示词修改前后的质量变化。评测同时计算 evidenceRecall，验证期望证据是否真正进入召回上下文，而不是只看最终答案关键词。

## 12. 知识健康运营

`KnowledgeHealthService` 聚合用户可见项目中的 ACTIVE/SUPERSEDED 文档、决策、活跃/候选记忆、开放知识缺口与最近评测结果。指标只用于运营和优先级判断，不改变 Agent 的回答证据链。

## 13. 运行审计

每次回答保存 AgentRun，并支持按当前用户查询最近运行摘要。摘要包含 runId、会话、问题、状态、项目范围、引用 ID、创建时间和耗时。完整运行仍可通过 runId 获取。

## 14. 提示注入防护

所有检索证据和项目记忆在进入模型前都视为不可信数据。`UntrustedContentGuard` 检测并移除“忽略之前指令、泄露系统提示词、调用工具”等模式；OpenAI-compatible 提示词使用显式不可信证据边界，并声明证据中的指令不得被执行。权限和工具路由始终由服务端代码控制。

## 15. 运行取消

SSE 集成层维护取消令牌。客户端完成、超时或连接错误时，令牌置为取消状态；Harness 在 scope、memory、retrieve、answer 和 verify 等阶段边界检查令牌，随后停止后续检索与模型调用。当前版本不强制中断已经发出的单个阻塞请求，但会阻止后续阶段。同时设置 `maxRunMillis` 时间预算，在 scope、memory、retrieve、answer 等边界检查。

## 16. 回答反馈

反馈与 runId 和用户绑定，同一用户对同一运行只保留最新一条。反馈包含 HELPFUL/NOT_HELPFUL、备注和时间，并提供正向率汇总。反馈不会自动修改知识事实，只作为检索质量和回答体验的运营信号。

## 17. 多轮查询改写

Session 保存上一轮问题与项目范围。当新问题较短且包含“它、那、这个项目、继续、还有”等指代标记时，Harness 将上一轮问题和当前问题合成为上下文查询，用于 Query Plan、记忆召回、检索和回答生成。AgentRun 仍保存用户原始问题，Trace 单独记录 rewrite。

## 18. 浏览器安全基线

所有响应设置 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`、受限权限策略和 CSP。API 响应使用 `Cache-Control: no-store`，避免浏览器缓存敏感知识。

## 19. 运行时持久化

`WorkspacePersistenceService` 每 5 秒将项目、文档及生命周期、决策、记忆、缺口、反馈和评测历史写入 JSON 临时文件，再用原子替换写入正式状态文件。启动时在 DemoDataLoader 之前恢复；文件不存在时加载演示数据。正常关闭前还会执行一次同步保存。测试环境通过 `app.persistence.enabled=false` 隔离。

## 20. 项目角色

项目采用 OWNER / EDITOR / VIEWER 三级角色。OWNER 可管理成员和角色；EDITOR 可录入、归档文档以及治理记忆；VIEWER 只能查看、检索和导出。已有普通成员默认 EDITOR，负责人始终为 OWNER。角色配置随 WorkspaceState 持久化。

## 21. 权限与可靠性

- 服务端根据 `X-User-Id` 计算可见项目。
- 指定未授权项目时直接返回 `FORBIDDEN`。
- 未指定项目时只在可见项目集合中检索。
- 每次运行生成 runId 和阶段 Trace。
- 无证据返回 `ABSTAINED`，不生成引用。
- 引用校验会移除不存在的 evidence ID。
