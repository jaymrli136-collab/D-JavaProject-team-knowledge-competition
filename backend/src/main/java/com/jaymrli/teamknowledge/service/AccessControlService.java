package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AccessControlService {
    private static final List<String> FOLLOW_UP_MARKERS = List.of("它", "这个项目", "该项目", "上面", "刚才", "那");
    private final KnowledgeStore store;

    public AccessControlService(KnowledgeStore store) { this.store = store; }

    public List<Project> visibleProjects(String userId) {
        return store.allProjects().stream().filter(project -> project.canAccess(userId)).toList();
    }

    public Project requireAccessibleProject(String userId, String projectId) {
        Project project = store.findProject(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
        if (!project.canAccess(userId)) throw new ForbiddenException("当前用户无权访问该项目");
        return project;
    }

    public Set<String> resolveScope(String userId, List<String> requestedProjectIds, String question,
                                    Set<String> sessionProjectIds) {
        if (requestedProjectIds != null && !requestedProjectIds.isEmpty()) {
            LinkedHashSet<String> requested = new LinkedHashSet<>(requestedProjectIds);
            for (String projectId : requested) requireAccessibleProject(userId, projectId);
            return requested;
        }
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> mentioned = new LinkedHashSet<>();
        for (Project project : store.allProjects()) {
            String normalizedName = project.name().toLowerCase(Locale.ROOT);
            if (normalizedQuestion.contains(normalizedName)) {
                if (!project.canAccess(userId)) throw new ForbiddenException("问题涉及无权访问的项目");
                mentioned.add(project.id());
            }
        }
        if (!mentioned.isEmpty()) return mentioned;

        if (sessionProjectIds != null && !sessionProjectIds.isEmpty()
                && FOLLOW_UP_MARKERS.stream().anyMatch(normalizedQuestion::contains)) {
            boolean stillAllowed = sessionProjectIds.stream().allMatch(id -> store.findProject(id)
                    .map(project -> project.canAccess(userId)).orElse(false));
            if (stillAllowed) return new LinkedHashSet<>(sessionProjectIds);
        }

        return visibleProjects(userId).stream().map(Project::id)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
