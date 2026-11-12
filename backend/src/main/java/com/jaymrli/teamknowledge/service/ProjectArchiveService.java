package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.DocumentLifecycleView;
import com.jaymrli.teamknowledge.api.ApiDtos.ProjectArchiveView;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGap;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeSection;
import com.jaymrli.teamknowledge.domain.Domain.MemoryStatus;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectDecision;
import com.jaymrli.teamknowledge.domain.Domain.ProjectMemory;
import com.jaymrli.teamknowledge.repository.KnowledgeGapRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.repository.ProjectMemoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectArchiveService {
    private final KnowledgeStore store;
    private final ProjectMemoryRepository memoryRepository;
    private final KnowledgeGapRepository gapRepository;
    private final AccessControlService accessControl;

    public ProjectArchiveService(KnowledgeStore store, ProjectMemoryRepository memoryRepository,
                                 KnowledgeGapRepository gapRepository, AccessControlService accessControl) {
        this.store = store;
        this.memoryRepository = memoryRepository;
        this.gapRepository = gapRepository;
        this.accessControl = accessControl;
    }

    private int sizeOf(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
    }

    public ProjectArchiveView exportProject(String userId, String projectId) {
        Project project = accessControl.requireAccessibleProject(userId, projectId);
        List<KnowledgeDocument> documents = store.documentsForProjects(Set.of(projectId));
        List<DocumentLifecycleView> lifecycles = documents.stream()
                .map(document -> new DocumentLifecycleView(document.id(), store.status(document.id()).name(),
                        store.supersededBy(document.id())))
                .toList();
        List<ProjectDecision> decisions = store.decisionsForProjects(Set.of(projectId));
        List<ProjectMemory> memories = memoryRepository.forProject(projectId);
        List<KnowledgeGap> gaps = gapRepository.all().stream()
                .filter(gap -> gap.projectIds().size() == 1 && gap.projectIds().contains(projectId))
                .toList();
        return new ProjectArchiveView("project-atlas-v1", Instant.now(), project, documents, lifecycles,
                decisions, memories, gaps);
    }

    public ProjectArchiveView importProject(String userId, ProjectArchiveView archive) {
        if (archive == null || archive.project() == null) throw new IllegalArgumentException("归档内容无效");
        if (archive.schemaVersion() != null && !"project-atlas-v1".equals(archive.schemaVersion())) {
            throw new IllegalArgumentException("不支持的归档版本: " + archive.schemaVersion());
        }
        if (sizeOf(archive.documents()) > 1000 || sizeOf(archive.decisions()) > 2000
                || sizeOf(archive.memories()) > 2000 || sizeOf(archive.gaps()) > 2000) {
            throw new IllegalArgumentException("归档条目数量超过限制");
        }
        String projectId = "p_" + UUID.randomUUID();
        Project project = new Project(projectId, archive.project().name() + "（导入）",
                archive.project().description(), userId, Set.of(userId), "active");
        store.addProject(project);

        Map<String, String> documentIds = new LinkedHashMap<>();
        List<KnowledgeDocument> documents = archive.documents() == null ? List.of() : archive.documents();
        documents.stream().sorted(Comparator.comparingInt(KnowledgeDocument::version)).forEach(document -> {
            String documentId = "doc_" + UUID.randomUUID();
            documentIds.put(document.id(), documentId);
            List<KnowledgeSection> sections = document.sections().stream()
                    .map(section -> new KnowledgeSection("sec_" + UUID.randomUUID(), section.headingPath(),
                            section.content(), section.order()))
                    .toList();
            store.addDocument(new KnowledgeDocument(documentId, projectId, document.title(), document.sourceType(),
                    document.sourceUri(), document.version(), document.effectiveDate(), document.tags(),
                    document.l0Summary(), document.l1Overview(), sections, Instant.now(), document.lineageKey()));
        });

        if (archive.lifecycles() != null) {
            archive.lifecycles().stream()
                    .filter(item -> "ARCHIVED".equals(item.status()))
                    .map(item -> documentIds.get(item.documentId()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(store::archiveDocument);
        }
        if (archive.decisions() != null) {
            archive.decisions().forEach(decision -> store.addDecision(new ProjectDecision(
                    "d_" + UUID.randomUUID(), projectId, decision.title(), decision.decision(),
                    decision.rationale(), userId, documentIds.getOrDefault(decision.sourceDocumentId(), null),
                    decision.effectiveDate())));
        }
        if (archive.memories() != null) {
            archive.memories().forEach(memory -> memoryRepository.save(new ProjectMemory(
                    "mem_" + UUID.randomUUID(), projectId, memory.type(), memory.title(), memory.content(),
                    MemoryStatus.PROPOSED, memory.confidence(), "imported:" + memory.sourceRunId(),
                    memory.sourceEvidenceIds(), userId, Instant.now(), null, null, null, null)));
        }
        if (archive.gaps() != null) {
            archive.gaps().forEach(gap -> gapRepository.save(new KnowledgeGap(
                    "gap_" + UUID.randomUUID(), userId, gap.question(), Set.of(projectId),
                    gap.sourceRunIds().stream().map(id -> "imported:" + id).toList(), gap.status(),
                    gap.occurrences(), Instant.now(), Instant.now(),
                    gap.candidateDocumentId() == null ? null : documentIds.get(gap.candidateDocumentId()),
                    gap.resolvedBy() == null ? null : userId, gap.resolvedAt(), gap.resolutionNote())));
        }
        return exportProject(userId, projectId);
    }
}
