package com.jaymrli.teamknowledge.repository;

import com.jaymrli.teamknowledge.domain.Domain.DocumentStatus;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectDecision;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class KnowledgeStore {
    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, ProjectDecision> decisions = new ConcurrentHashMap<>();
    private final Map<String, Lifecycle> lifecycles = new ConcurrentHashMap<>();

    public void addProject(Project project) { projects.put(project.id(), project); }
    public synchronized void addDocument(KnowledgeDocument document) {
        String lineage = lineageOf(document);
        KnowledgeDocument newer = documents.values().stream()
                .filter(item -> item.projectId().equals(document.projectId()))
                .filter(item -> lineageOf(item).equals(lineage))
                .filter(item -> item.version() > document.version())
                .max(Comparator.comparingInt(KnowledgeDocument::version)).orElse(null);
        if (newer != null) {
            lifecycles.put(document.id(), new Lifecycle(DocumentStatus.SUPERSEDED, newer.id()));
        } else {
            documents.values().stream()
                    .filter(item -> item.projectId().equals(document.projectId()))
                    .filter(item -> lineageOf(item).equals(lineage))
                    .filter(item -> item.version() <= document.version())
                    .forEach(item -> lifecycles.put(item.id(), new Lifecycle(DocumentStatus.SUPERSEDED, document.id())));
            lifecycles.put(document.id(), new Lifecycle(DocumentStatus.ACTIVE, null));
        }
        documents.put(document.id(), document);
    }
    public void addDecision(ProjectDecision decision) { decisions.put(decision.id(), decision); }
    public synchronized void restore(List<Project> restoredProjects, List<KnowledgeDocument> restoredDocuments,
                                     List<ProjectDecision> restoredDecisions,
                                     Map<String, String> documentStatuses,
                                     Map<String, String> supersededDocuments) {
        projects.clear();
        documents.clear();
        decisions.clear();
        lifecycles.clear();
        for (Project project : restoredProjects) projects.put(project.id(), project);
        for (KnowledgeDocument document : restoredDocuments) {
            documents.put(document.id(), document);
            DocumentStatus status = DocumentStatus.valueOf(documentStatuses.getOrDefault(document.id(), "ACTIVE"));
            lifecycles.put(document.id(), new Lifecycle(status, supersededDocuments.get(document.id())));
        }
        for (ProjectDecision decision : restoredDecisions) decisions.put(decision.id(), decision);
    }

    public Optional<Project> findProject(String projectId) { return Optional.ofNullable(projects.get(projectId)); }
    public List<Project> allProjects() { return projects.values().stream().sorted(Comparator.comparing(Project::name)).toList(); }

    public List<KnowledgeDocument> documentsForProjects(Set<String> projectIds) {
        return documents.values().stream()
                .filter(doc -> projectIds.contains(doc.projectId()))
                .sorted(Comparator.comparing(KnowledgeDocument::effectiveDate).reversed().thenComparing(KnowledgeDocument::title))
                .toList();
    }

    public List<KnowledgeDocument> activeDocumentsForProjects(Set<String> projectIds) {
        return documentsForProjects(projectIds).stream()
                .filter(doc -> status(doc.id()) == DocumentStatus.ACTIVE)
                .toList();
    }

    public Optional<KnowledgeDocument> findDocument(String documentId) { return Optional.ofNullable(documents.get(documentId)); }

    public List<KnowledgeDocument> historyFor(String documentId) {
        KnowledgeDocument anchor = documents.get(documentId);
        if (anchor == null) return List.of();
        String lineage = lineageOf(anchor);
        return documents.values().stream()
                .filter(item -> item.projectId().equals(anchor.projectId()))
                .filter(item -> lineageOf(item).equals(lineage))
                .sorted(Comparator.comparingInt(KnowledgeDocument::version).reversed())
                .toList();
    }

    public List<KnowledgeDocument> allDocuments() {
        return documents.values().stream().sorted(Comparator.comparing(KnowledgeDocument::indexedAt).reversed()).toList();
    }

    public long documentCount(String projectId) {
        return documents.values().stream()
                .filter(doc -> doc.projectId().equals(projectId))
                .filter(doc -> status(doc.id()) == DocumentStatus.ACTIVE)
                .count();
    }

    public DocumentStatus status(String documentId) {
        return lifecycles.getOrDefault(documentId, new Lifecycle(DocumentStatus.ACTIVE, null)).status();
    }

    public synchronized void archiveDocument(String documentId) {
        if (!documents.containsKey(documentId)) return;
        lifecycles.put(documentId, new Lifecycle(DocumentStatus.ARCHIVED, null));
    }

    public synchronized void restoreDocument(String documentId) {
        KnowledgeDocument document = documents.get(documentId);
        if (document == null) return;
        String lineage = lineageOf(document);
        KnowledgeDocument newer = documents.values().stream()
                .filter(item -> !item.id().equals(documentId))
                .filter(item -> item.projectId().equals(document.projectId()))
                .filter(item -> lineageOf(item).equals(lineage))
                .filter(item -> item.version() > document.version())
                .filter(item -> status(item.id()) == DocumentStatus.ACTIVE)
                .max(Comparator.comparingInt(KnowledgeDocument::version)).orElse(null);
        lifecycles.put(documentId, newer == null
                ? new Lifecycle(DocumentStatus.ACTIVE, null)
                : new Lifecycle(DocumentStatus.SUPERSEDED, newer.id()));
    }

    public String supersededBy(String documentId) {
        return lifecycles.getOrDefault(documentId, new Lifecycle(DocumentStatus.ACTIVE, null)).supersededBy();
    }

    public long decisionCount(String projectId) {
        return decisions.values().stream().filter(decision -> decision.projectId().equals(projectId)).count();
    }

    public List<ProjectDecision> decisionsForProjects(Set<String> projectIds) {
        List<ProjectDecision> result = new ArrayList<>();
        for (ProjectDecision decision : decisions.values()) {
            if (projectIds.contains(decision.projectId())) result.add(decision);
        }
        return result;
    }

    public Optional<ProjectDecision> findDecision(String decisionId) { return Optional.ofNullable(decisions.get(decisionId)); }

    private String lineageOf(KnowledgeDocument document) {
        if (document.lineageKey() != null && !document.lineageKey().isBlank()) {
            return document.projectId() + ":" + document.lineageKey().trim().toLowerCase();
        }
        String source = document.sourceUri() == null ? document.title() : document.sourceUri();
        source = source.toLowerCase()
                .replaceAll("[?#].*$", "")
                .replaceAll("[-_/]v\\d+($|/)", "")
                .replaceAll("[-_/]\\d+($|/)", "")
                .replaceAll("/+$", "");
        return document.projectId() + ":" + source;
    }

    private record Lifecycle(DocumentStatus status, String supersededBy) {
    }
}
