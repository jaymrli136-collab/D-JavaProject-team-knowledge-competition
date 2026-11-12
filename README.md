# Team Knowledge Competition

> 面向知识密集型、多项目并行团队的可信知识问答 Agent Harness。

本项目严格围绕「新生意创造者 AI 挑战赛」第 5 题开发。系统把分散在多个项目中的文档、决策、风险和经验组织为可检索、可引用、可评测的知识上下文，并通过显式 Agent Harness 完成任务规划、证据检索、回答校验和拒答。

## 核心能力

- 多项目知识命名空间与成员 ACL，权限过滤发生在检索之前。
- L0 摘要、L1 文档概览、L2 章节原文的渐进式上下文加载。
- BM25 + 轻量哈希向量混合召回，可在无外部模型时直接运行。
- 显式 Agent Harness：Scope → Plan → Retrieve → Answer → Verify → Trace。
- 每个关键结论返回项目、文档、章节、版本与原文证据。
- 无证据时拒答，不跨项目权限边界泄漏信息。
- 受治理的项目记忆：从可信回答生成候选、人工确认、可撤回并保留来源；仅 ACTIVE 记忆进入软上下文。
- 知识缺口闭环：拒答自动记录缺口、累计重复次数并支持解决/忽略。
- SSE 流式执行：实时展示 scope、memory、tool、retrieve、answer、verify、gap 阶段。
- 文档生命周期：同一谱系新版本自动取代旧版，检索仅使用 ACTIVE 文档。
- 引用可下钻到项目、文档版本、章节和完整原文。
- 固定评测历史：保存通过率、引用覆盖、证据召回、拒答准确率和权限违规趋势。
- 单次回答引用覆盖率：量化回答中有多少实质陈述绑定了证据。
- 知识健康总览：聚合 ACTIVE 文档、候选记忆、开放缺口和最近评测通过率。
- 运行审计：查看当前用户最近的问题、状态、引用、耗时和会话。
- 缺口与摄入联动：新文档匹配开放缺口后进入待复核，不会自动伪装为已解决。
- 提示注入防护：知识证据按不可信数据处理，模型提示词会移除指令注入片段。
- SSE 取消控制：客户端断开或超时后，Harness 在阶段边界停止后续检索和模型调用。
- 回答反馈：按 runId 记录“有帮助/需改进”反馈并汇总正向率。
- 多轮上下文查询：短追问自动融合上一轮问题用于检索与规划，原文问题仍保留。
- 文档接入：Markdown/TXT 本地预览，PDF/DOCX 服务端解析后进入统一摄入流程。
- 可选真实 Embedding：OpenAI-compatible `/embeddings`，带缓存和离线哈希回退。
- 项目管理：创建知识项目并由负责人维护成员范围。
- 项目角色：OWNER / EDITOR / VIEWER，编辑与成员管理分级授权。
- 项目归档：导出/导入完整知识项目，导入时重建 ID 并把记忆重置为候选状态。
- 运行时持久化：每 5 秒原子写入 D 盘 JSON，并在启动时恢复工作区状态。
- 安全响应头：CSP、nosniff、frame deny、referrer policy 和 API no-store。
- 检索预览：独立查看查询计划、记忆召回和证据，不生成回答。
- 运行预算：限制证据数量和单次运行时长，超预算后停止后续阶段。
- 内置演示知识库、固定评测任务和可运行前端。

## 技术栈

- Java 17 / Spring Boot 3
- 内存知识库与检索索引
- BM25 + 哈希向量混合检索
- 可选 OpenAI-compatible 模型接口
- 原生 HTML/CSS/JavaScript 演示界面

## 启动

```powershell
cd D:\JavaProject\team-knowledge-competition
.\scripts\run.ps1
```

打开 `http://localhost:8088`。

默认使用内置知识库和确定性 Mock Answer Generator，无需 API Key。接入真实模型时：

```powershell
$env:LLM_PROVIDER="openai"
$env:LLM_BASE_URL="https://api.openai.com/v1"
$env:LLM_API_KEY="<your-key>"
$env:LLM_MODEL="gpt-4.1-mini"
$env:LLM_EMBEDDING_MODEL="text-embedding-3-small"
.\scripts\run.ps1
```

## 演示用户

| 用户 | 可见项目 |
|---|---|
| `u_alice` | 番茄小说增长实验、红果短剧内容推荐 |
| `u_bob` | 番茄小说增长实验、头条热点专题 |
| `u_carol` | 红果短剧内容推荐 |

前端右上角可以切换用户，用于演示项目隔离与越权拒答。

## Docker

```powershell
docker compose up --build
```

状态文件挂载到 `./data/workspace-state.json`。

## 文档

- [产品需求文档](docs/product/PRODUCT_REQUIREMENTS.md)
- [验收标准](docs/product/ACCEPTANCE_CRITERIA.md)
- [系统架构](docs/architecture/ARCHITECTURE.md)
- [数据模型](docs/architecture/DATA_MODEL.md)
- [实现状态](docs/development/IMPLEMENTATION_STATUS.md)
- [API 手册](docs/api/API.md)
- [比赛演示脚本](docs/product/DEMO_SCRIPT.md)
- [变更记录](CHANGELOG.md)
- [测试计划](docs/development/TEST_PLAN.md)
- [部署说明](docs/development/DEPLOYMENT.md)

## 测试

```powershell
cd D:\JavaProject\team-knowledge-competition
.\scripts\test.ps1
```

Maven 下载、构建产物和测试临时文件统一使用项目内 D 盘目录；本地 Maven 运行时位于 `.tools`，不占用 C 盘。
