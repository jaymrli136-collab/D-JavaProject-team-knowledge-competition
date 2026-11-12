package com.jaymrli.teamknowledge.repository;

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

    public void addProject(Project project) { projects.put(project.id(), project); }
    public void addDocument(KnowledgeDocument document) { documents.put(document.id(), document); }
    public void addDecision(ProjectDecision decision) { decisions.put(decision.id(), decision); }
    public Optional<Project> findProject(String projectId) { return Optional.ofNullable(projects.get(projectId)); }
    public List<Project> allProjects() { return projects.values().stream().sorted(Comparator.comparing(Project::name)).toList(); }

    public List<KnowledgeDocument> documentsForProjects(Set<String> projectIds) {
        return documents.values().stream()
                .filter(doc -> projectIds.contains(doc.projectId()))
                .sorted(Comparator.comparing(KnowledgeDocument::effectiveDate).reversed().thenComparing(KnowledgeDocument::title))
                .toList();
    }

    public List<KnowledgeDocument> allDocuments() {
        return documents.values().stream().sorted(Comparator.comparing(KnowledgeDocument::indexedAt).reversed()).toList();
    }

    public long documentCount(String projectId) {
        return documents.values().stream().filter(doc -> doc.projectId().equals(projectId)).count();
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
}
