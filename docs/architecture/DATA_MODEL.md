# 数据模型

## 核心实体

- **Project**：项目名称、负责人、成员、状态。
- **KnowledgeDocument**：项目、标题、来源、版本、谱系、有效期、L0/L1 摘要、章节；生命周期包含 ACTIVE / SUPERSEDED。
- **KnowledgeSection**：标题路径、正文、顺序。
- **ProjectDecision**：决策、原因、负责人、来源文档、有效期。
- **AgentRun**：问题、用户、项目范围、状态、证据、引用、Trace、耗时。
- **ProjectMemory**：项目、类型、内容、状态、来源运行、来源证据、创建/确认/撤回人和时间。
- **KnowledgeGap**：问题、项目范围、来源运行集合、出现次数、处理状态与处理说明。
- **EvaluationSnapshot**：评测时间、用例数、通过数、通过率、引用覆盖、拒答准确率和权限违规数。

## 检索文档

索引层 `IndexedChunk` 包含项目、文档、章节、标题路径、正文、词项计数、文档长度、归一化哈希向量、版本和来源。

## 证据与引用

Evidence 是 Agent 实际看到的上下文，包含 evidenceId、来源和片段。Citation 是最终回答使用并经过校验的证据。二者保持一对一关系，禁止生成没有 Evidence 支撑的 Citation。

## Session Memory

```text
sessionId -> { lastQuestion, lastProjectIds, lastRunId, updatedAt }
```

Session Memory 只用于会话连续性，不进入项目权威知识库。
