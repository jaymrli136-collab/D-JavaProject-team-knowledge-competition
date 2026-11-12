package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.KnowledgeHealthView;
import com.jaymrli.teamknowledge.domain.Domain.DocumentStatus;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGapStatus;
import com.jaymrli.teamknowledge.domain.Domain.MemoryStatus;
import com.jaymrli.teamknowledge.repository.EvaluationHistoryRepository;
import com.jaymrli.teamknowledge.repository.FeedbackRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeGapRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.repository.ProjectMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class KnowledgeHealthService {
    private final KnowledgeStore knowledgeStore;
    private final ProjectMemoryRepository memoryRepository;
    private final KnowledgeGapRepository gapRepository;
    private final EvaluationHistoryRepository evaluationHistory;
    private final FeedbackRepository feedbackRepository;
    private final AccessControlService accessControl;

    public KnowledgeHealthService(KnowledgeStore knowledgeStore, ProjectMemoryRepository memoryRepository,
                                  KnowledgeGapRepository gapRepository, EvaluationHistoryRepository evaluationHistory,
                                  FeedbackRepository feedbackRepository, AccessControlService accessControl) {
        this.knowledgeStore = knowledgeStore;
        this.memoryRepository = memoryRepository;
        this.gapRepository = gapRepository;
        this.evaluationHistory = evaluationHistory;
        this.feedbackRepository = feedbackRepository;
        this.accessControl = accessControl;
    }

    public KnowledgeHealthView overview(String userId, String projectId) {
        Set<String> visible = new LinkedHashSet<>();
        if (projectId == null || projectId.isBlank()) {
            accessControl.visibleProjects(userId).forEach(project -> visible.add(project.id()));
        } else {
            accessControl.requireAccessibleProject(userId, projectId);
            visible.add(projectId);
        }
        int active = 0;
        int superseded = 0;
        int archived = 0;
        for (var document : knowledgeStore.allDocuments()) {
            if (!visible.contains(document.projectId())) continue;
            DocumentStatus status = knowledgeStore.status(document.id());
            if (status == DocumentStatus.ACTIVE) active++;
            else if (status == DocumentStatus.SUPERSEDED) superseded++;
            else archived++;
        }
        int decisions = visible.stream().mapToInt(id -> (int) knowledgeStore.decisionCount(id)).sum();
        int activeMemories = 0;
        int proposedMemories = 0;
        for (var memory : memoryRepository.all()) {
            if (!visible.contains(memory.projectId())) continue;
            if (memory.status() == MemoryStatus.ACTIVE) activeMemories++;
            if (memory.status() == MemoryStatus.PROPOSED) proposedMemories++;
        }
        int openGaps = (int) gapRepository.all().stream()
                .filter(item -> item.status() == KnowledgeGapStatus.OPEN || item.status() == KnowledgeGapStatus.IN_REVIEW)
                .filter(item -> item.createdBy().equals(userId) || visible.containsAll(item.projectIds()))
                .count();
        var latest = evaluationHistory.recent(1).stream().findFirst().orElse(null);
        var feedback = feedbackRepository.all();
        long helpful = feedback.stream().filter(item -> item.rating().name().equals("HELPFUL")).count();
        double helpfulRate = feedback.isEmpty() ? 1.0 : Math.round((double) helpful / feedback.size() * 10000.0) / 10000.0;
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (active == 0) warnings.add("没有可检索的 ACTIVE 文档");
        if (openGaps > 0) warnings.add("存在 " + openGaps + " 个待处理知识缺口");
        if (latest == null) warnings.add("尚未运行固定评测");
        if (feedback.size() >= 5 && helpfulRate < 0.6) warnings.add("反馈正向率低于 60%");
        return new KnowledgeHealthView(active, superseded, archived, decisions, activeMemories, proposedMemories,
                openGaps, feedback.size(), helpfulRate, latest == null ? null : latest.passRate(),
                latest == null ? null : latest.createdAt(), warnings);
    }
}
