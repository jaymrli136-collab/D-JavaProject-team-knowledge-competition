package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.EvidenceView;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryHintView;
import com.jaymrli.teamknowledge.api.ApiDtos.RetrievalPreviewRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.RetrievalPreviewView;
import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.Plan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class RetrievalPreviewService {
    private final AccessControlService accessControl;
    private final QueryPlanner queryPlanner;
    private final HybridRetriever retriever;
    private final ProjectMemoryContextService memoryContext;
    private final TeamKnowledgeProperties properties;

    public RetrievalPreviewService(AccessControlService accessControl, QueryPlanner queryPlanner,
                                   HybridRetriever retriever, ProjectMemoryContextService memoryContext,
                                   TeamKnowledgeProperties properties) {
        this.accessControl = accessControl;
        this.queryPlanner = queryPlanner;
        this.retriever = retriever;
        this.memoryContext = memoryContext;
        this.properties = properties;
    }

    public RetrievalPreviewView preview(String userId, RetrievalPreviewRequest request) {
        Set<String> scope = accessControl.resolveScope(userId, request.projectIds(), request.question(), Set.of());
        Plan plan = queryPlanner.plan(request.question(), scope);
        List<Evidence> evidence = retriever.retrieve(request.question(), scope, plan.intent(),
                properties.retrieval().maxEvidence());
        List<EvidenceView> evidenceViews = evidence.stream()
                .map(item -> new EvidenceView(item.evidenceId(), item.projectId(), item.projectName(),
                        item.documentId(), item.documentTitle(), item.sectionId(), item.headingPath(),
                        item.snippet(), item.effectiveDate(), item.finalScore()))
                .toList();
        List<MemoryHintView> memoryHints = memoryContext.retrieve(request.question(), scope, 3).stream()
                .map(item -> new MemoryHintView(item.memoryId(), item.projectId(), item.projectName(),
                        item.type(), item.title(), item.content(), item.score(), item.sourceEvidenceIds()))
                .toList();
        return new RetrievalPreviewView(scope.stream().toList(), plan.intent().name(), plan.queries(),
                evidenceViews, memoryHints);
    }
}
