package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.Intent;
import com.jaymrli.teamknowledge.domain.Domain.ToolExecution;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class KnowledgeSearchTool {
    private final HybridRetriever retriever;

    public KnowledgeSearchTool(HybridRetriever retriever) { this.retriever = retriever; }

    public String name() { return "search_knowledge"; }

    public String description() { return "在权限范围内执行 BM25 与哈希向量混合召回"; }

    public ToolExecution execute(String query, Set<String> projectIds, Intent intent, int limit) {
        String reason = "意图=" + intent + "，范围=" + String.join(",", projectIds)
                + "，混合检索 topK=" + limit;
        return new ToolExecution(name(), reason, retriever.retrieve(query, projectIds, intent, limit));
    }
}
