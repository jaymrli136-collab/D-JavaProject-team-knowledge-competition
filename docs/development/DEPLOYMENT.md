# 部署说明

## Docker Compose

```powershell
docker compose up --build
```

访问 `http://localhost:8088`。

持久化文件挂载在宿主机项目目录的 `data/workspace-state.json`。项目、文档版本、决策、记忆、知识缺口、反馈和评测历史会在 Docker 重启后恢复。

## 真实模型

```powershell
$env:LLM_PROVIDER="openai"
$env:LLM_BASE_URL="https://api.openai.com/v1"
$env:LLM_API_KEY="<your-key>"
$env:LLM_MODEL="gpt-4.1-mini"
$env:LLM_EMBEDDING_MODEL="text-embedding-3-small"
docker compose up --build
```

API Key 只通过环境变量注入，不写入镜像和持久化文件。

## 健康检查

- 应用：`GET /api/health`
- Actuator：`GET /actuator/health`
- 知识健康：`GET /api/health/overview`
- 运行时配置：`GET /api/runtime`
