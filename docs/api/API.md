# API 手册

所有业务接口前缀为 `/api`。演示环境通过 `X-User-Id` 识别用户，默认 `u_alice`。

## 知识项目

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/users` | 已知演示用户目录 |
| GET | `/projects` | 当前用户可见项目 |
| POST | `/projects` | 创建知识项目 |
| POST | `/projects/{projectId}/members` | 负责人更新项目成员 |
| POST | `/projects/{projectId}/roles` | 负责人设置成员角色 |
| GET | `/projects/{projectId}/archive` | 导出项目知识归档 |
| POST | `/projects/archive/import` | 导入为当前用户拥有的新项目 |
| GET | `/health/overview` | 知识健康汇总 |
| GET | `/runtime` | 只读模型、Embedding、证据和预算配置 |
| POST | `/persistence/save` | 立即持久化当前工作区 |
| GET | `/projects/{projectId}/documents` | 项目文档与生命周期 |
| POST | `/projects/{projectId}/documents` | 摄入 Markdown 文本 |
| POST | `/projects/{projectId}/documents/upload` | multipart 上传并解析 PDF/DOCX/TXT/MD |
| GET | `/documents/{documentId}/sections/{sectionId}` | 获取引用原文 |
| GET | `/documents/{documentId}/history` | 同一谱系的完整版本链 |
| GET | `/documents/{documentId}/diff/{otherDocumentId}` | 比较两个文档版本的增删行 |
| POST | `/documents/{documentId}/archive` | 归档文档并停止参与检索 |
| POST | `/documents/{documentId}/restore` | 恢复归档文档，按版本自动回落 |

## Agent

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent/ask` | 同步问答 |
| POST | `/agent/ask/stream` | SSE 阶段事件与最终回答 |
| POST | `/retrieval/preview` | 仅执行查询计划、记忆召回和检索 |
| GET | `/runs/metrics` | 当前用户运行数、耗时和引用覆盖聚合 |
| GET | `/runs?limit=20` | 当前用户最近运行 |
| GET | `/runs/{runId}` | 完整运行记录 |
| POST | `/runs/{runId}/feedback` | 提交帮助度反馈 |
| GET | `/feedback/summary` | 反馈汇总 |

SSE 事件名包括 `trace`、`result` 和 `error`。

## 记忆与缺口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/memories?projectId=` | 当前用户可见记忆，可按项目过滤 |
| POST | `/memories/propose` | 从可信运行生成候选记忆 |
| POST | `/memories/{id}/confirm` | 确认记忆 |
| POST | `/memories/{id}/retire` | 撤回记忆 |
| GET | `/gaps?projectId=` | 知识缺口列表，可按项目过滤 |
| GET | `/gaps/export?projectId=` | 导出 Markdown 缺口清单 |
| POST | `/gaps/{id}/resolve` | 标记已解决 |
| POST | `/gaps/{id}/ignore` | 忽略缺口 |

## 评测

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/evaluations/cases` | 查看固定评测用例与期望证据 |
| POST | `/evaluations/run` | 运行固定评测 |
| GET | `/evaluations/history?limit=10` | 最近评测指标 |

## 错误码

| code | HTTP | 含义 |
|---|---:|---|
| FORBIDDEN | 403 | 项目越权或角色不足 |
| NOT_FOUND | 404 | 项目、文档、章节或运行不存在 |
| INVALID_REQUEST | 400 | 参数校验失败 |
| MALFORMED_JSON | 400 | JSON 无法解析 |
| MISSING_PART | 400 | multipart 缺少文件字段 |
| PAYLOAD_TOO_LARGE | 413 | 上传超过 10 MB |
| INVALID_OPERATION | 400 | 业务状态不允许该操作 |
