package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.KnowledgeGapView;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGap;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGapStatus;
import com.jaymrli.teamknowledge.repository.KnowledgeGapRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeGapService {
    private final KnowledgeGapRepository repository;
    private final KnowledgeStore knowledgeStore;
    private final AccessControlService accessControl;
    private final TextAnalyzer analyzer;

    public KnowledgeGapService(KnowledgeGapRepository repository, KnowledgeStore knowledgeStore,
                               AccessControlService accessControl, TextAnalyzer analyzer) {
        this.repository = repository;
        this.knowledgeStore = knowledgeStore;
        this.accessControl = accessControl;
        this.analyzer = analyzer;
    }

    public KnowledgeGap capture(String userId, String question, Set<String> projectIds, String runId) {
        Set<String> scope = Set.copyOf(projectIds);
        KnowledgeGap existing = repository.findOpen(userId, question, scope).orElse(null);
        if (existing != null) {
            List<String> runs = new ArrayList<>(existing.sourceRunIds());
            if (!runs.contains(runId)) runs.add(runId);
            KnowledgeGap updated = new KnowledgeGap(existing.id(), existing.createdBy(), existing.question(),
                    existing.projectIds(), List.copyOf(runs), existing.status(), existing.occurrences() + 1,
                    existing.createdAt(), Instant.now(), existing.candidateDocumentId(), existing.resolvedBy(),
                    existing.resolvedAt(), existing.resolutionNote());
            return repository.save(updated);
        }
        KnowledgeGap gap = new KnowledgeGap("gap_" + UUID.randomUUID(), userId, question, scope,
                List.of(runId), KnowledgeGapStatus.OPEN, 1, Instant.now(), Instant.now(), null,
                null, null, null);
        return repository.save(gap);
    }

    public List<KnowledgeGap> inspectNewDocument(String projectId, String documentId) {
        KnowledgeDocument document = knowledgeStore.findDocument(documentId).orElse(null);
        if (document == null) return List.of();
        Set<String> documentTokens = tokens(document.title() + " " + document.l0Summary() + " "
                + document.l1Overview() + " " + document.sections().stream()
                .map(section -> section.headingPath() + " " + section.content())
                .collect(java.util.stream.Collectors.joining(" ")));
        List<KnowledgeGap> updated = new ArrayList<>();
        for (KnowledgeGap gap : repository.all()) {
            if (!gap.projectIds().contains(projectId) || gap.status() != KnowledgeGapStatus.OPEN) continue;
            Set<String> questionTokens = tokens(gap.question());
            long overlap = questionTokens.stream().filter(documentTokens::contains).count();
            if (overlap >= Math.min(2, Math.max(1, questionTokens.size()))) {
                KnowledgeGap candidate = new KnowledgeGap(gap.id(), gap.createdBy(), gap.question(),
                        gap.projectIds(), gap.sourceRunIds(), KnowledgeGapStatus.IN_REVIEW, gap.occurrences(),
                        gap.createdAt(), gap.lastSeenAt(), document.id(), gap.resolvedBy(), gap.resolvedAt(),
                        gap.resolutionNote());
                updated.add(repository.save(candidate));
            }
        }
        return updated;
    }

    public List<KnowledgeGapView> listVisible(String userId) {
        return listVisible(userId, null);
    }

    public List<KnowledgeGapView> listVisible(String userId, String projectId) {
        Set<String> visible = new LinkedHashSet<>();
        accessControl.visibleProjects(userId).forEach(project -> visible.add(project.id()));
        if (projectId != null && !projectId.isBlank()) {
            accessControl.requireAccessibleProject(userId, projectId);
            visible.retainAll(Set.of(projectId));
        }
        return repository.all().stream()
                .filter(item -> item.createdBy().equals(userId) || (!item.projectIds().isEmpty() && visible.containsAll(item.projectIds())))
                .filter(item -> projectId == null || projectId.isBlank() || item.projectIds().contains(projectId))
                .map(this::toView)
                .toList();
    }

    public String exportMarkdown(String userId, String projectId) {
        List<KnowledgeGapView> gaps = listVisible(userId, projectId);
        StringBuilder report = new StringBuilder("# 知识缺口清单\n\n");
        if (gaps.isEmpty()) return report.append("当前没有知识缺口。\n").toString();
        for (KnowledgeGapView gap : gaps) {
            report.append("## ").append(gap.question()).append("\n\n")
                    .append("- 状态：").append(gap.status()).append("\n")
                    .append("- 出现次数：").append(gap.occurrences()).append("\n")
                    .append("- 项目：").append(String.join("、", gap.projectNames())).append("\n")
                    .append("- 创建人：").append(gap.createdBy()).append("\n")
                    .append("- 最近出现：").append(gap.lastSeenAt()).append("\n");
            if (gap.candidateDocumentTitle() != null) {
                report.append("- 候选文档：").append(gap.candidateDocumentTitle()).append("\n");
            }
            if (gap.resolutionNote() != null && !gap.resolutionNote().isBlank()) {
                report.append("- 处理说明：").append(gap.resolutionNote()).append("\n");
            }
            report.append("\n");
        }
        return report.toString();
    }

    public KnowledgeGapView resolve(String userId, String gapId, String note) {
        return updateStatus(userId, gapId, KnowledgeGapStatus.RESOLVED, note);
    }

    public KnowledgeGapView ignore(String userId, String gapId, String note) {
        return updateStatus(userId, gapId, KnowledgeGapStatus.IGNORED, note);
    }

    private KnowledgeGapView updateStatus(String userId, String gapId, KnowledgeGapStatus status, String note) {
        KnowledgeGap gap = repository.find(gapId)
                .orElseThrow(() -> new NotFoundException("知识缺口不存在: " + gapId));
        boolean creator = gap.createdBy().equals(userId);
        boolean allVisible = gap.projectIds().stream().allMatch(id -> knowledgeStore.findProject(id)
                .map(project -> project.canAccess(userId)).orElse(false));
        if (!creator && !allVisible) throw new ForbiddenException("无权处理该知识缺口");
        KnowledgeGap updated = new KnowledgeGap(gap.id(), gap.createdBy(), gap.question(), gap.projectIds(),
                gap.sourceRunIds(), status, gap.occurrences(), gap.createdAt(), gap.lastSeenAt(),
                gap.candidateDocumentId(), userId, Instant.now(), note == null ? "" : note.trim());
        return toView(repository.save(updated));
    }

    private Set<String> tokens(String text) {
        return new LinkedHashSet<>(analyzer.tokenize(text).stream()
                .filter(token -> token.length() > 1 || token.chars().allMatch(code -> code < 128))
                .toList());
    }

    private KnowledgeGapView toView(KnowledgeGap gap) {
        List<String> names = gap.projectIds().stream()
                .map(id -> knowledgeStore.findProject(id).map(project -> project.name()).orElse(id))
                .toList();
        String candidateTitle = gap.candidateDocumentId() == null ? null
                : knowledgeStore.findDocument(gap.candidateDocumentId()).map(KnowledgeDocument::title).orElse(null);
        return new KnowledgeGapView(gap.id(), gap.createdBy(), gap.question(), gap.projectIds(), names,
                gap.sourceRunIds(), gap.status().name(), gap.occurrences(), gap.createdAt(), gap.lastSeenAt(),
                gap.candidateDocumentId(), candidateTitle, gap.resolvedBy(), gap.resolvedAt(), gap.resolutionNote());
    }
}
