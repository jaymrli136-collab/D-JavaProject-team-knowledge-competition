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
- 受治理的项目记忆：从可信回答生成候选、人工确认、可撤回并保留来源。
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
mvn -Dmaven.repo.local=D:\JavaProject\.m2 spring-boot:run
```

## 演示用户

| 用户 | 可见项目 |
|---|---|
| `u_alice` | 番茄小说增长实验、红果短剧内容推荐 |
| `u_bob` | 番茄小说增长实验、头条热点专题 |
| `u_carol` | 红果短剧内容推荐 |

前端右上角可以切换用户，用于演示项目隔离与越权拒答。

## 文档

- [产品需求文档](docs/product/PRODUCT_REQUIREMENTS.md)
- [验收标准](docs/product/ACCEPTANCE_CRITERIA.md)
- [系统架构](docs/architecture/ARCHITECTURE.md)
- [数据模型](docs/architecture/DATA_MODEL.md)
- [实现状态](docs/development/IMPLEMENTATION_STATUS.md)

## 测试

```powershell
cd D:\JavaProject\team-knowledge-competition
.\scripts\test.ps1
```

所有 Maven 下载和缓存统一使用 `D:\JavaProject\.m2`，不占用 C 盘。
