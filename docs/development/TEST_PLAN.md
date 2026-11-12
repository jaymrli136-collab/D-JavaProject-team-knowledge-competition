# 测试计划

## 自动测试

```powershell
.scripts	est.ps1
```

覆盖领域、权限、Agent Harness、记忆、缺口、版本生命周期、反馈和集成上下文。

## API 冒烟

先启动应用：

```powershell
.scriptsun.ps1
```

另开终端：

```powershell
.scriptssmoke.ps1
```

## 持久化验证

1. 启动应用并录入一份文档。
2. 调用 `POST /api/persistence/save` 或等待 5 秒。
3. 重启应用。
4. 确认项目、文档、记忆、缺口和评测状态恢复。

## 手工验收

1. 切换 `u_alice/u_bob/u_carol`，确认项目范围变化。
2. 提问风险问题，确认结论、引用、覆盖率、SSE Trace。
3. 点击证据原文，确认章节打开且命中片段高亮。
4. 连续追问，确认 Trace 出现 rewrite。
5. 使用无答案问题，确认 ABSTAINED 和知识缺口。
6. 录入匹配文档，确认缺口进入 IN_REVIEW。
7. 从可信回答沉淀记忆，确认后才可进入软上下文。
8. 查看文档版本链，确认旧版本 SUPERSEDED。
9. 运行评测并查看历史趋势。
10. 切换成员并尝试越权项目，确认 FORBIDDEN 且无文档泄漏。
