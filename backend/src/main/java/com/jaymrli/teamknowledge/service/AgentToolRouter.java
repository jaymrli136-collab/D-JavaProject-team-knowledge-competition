package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.Intent;
import com.jaymrli.teamknowledge.domain.Domain.Plan;
import com.jaymrli.teamknowledge.domain.Domain.ToolExecution;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AgentToolRouter {
    private final KnowledgeSearchTool searchTool;

    public AgentToolRouter(KnowledgeSearchTool searchTool) { this.searchTool = searchTool; }

    public List<String> route(Plan plan) {
        if (plan.intent() == Intent.COMPARISON) return List.of("search_knowledge", "compare_evidence");
        return List.of("search_knowledge");
    }

    public ToolExecution search(String query, Set<String> projectIds, Intent intent, int limit) {
        return searchTool.execute(query, projectIds, intent, limit);
    }
}
