package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.CreateProjectRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.ProjectView;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectManagementService {
    private final KnowledgeStore store;
    private final ProjectRoleService roleService;

    public ProjectManagementService(KnowledgeStore store, ProjectRoleService roleService) {
        this.store = store;
        this.roleService = roleService;
    }

    public ProjectView create(String userId, CreateProjectRequest request) {
        Set<String> members = new LinkedHashSet<>();
        members.add(userId);
        if (request.memberIds() != null) members.addAll(request.memberIds());
        Project project = new Project("p_" + UUID.randomUUID(), request.name().trim(),
                request.description() == null ? "" : request.description().trim(), userId,
                Set.copyOf(members), "active");
        store.addProject(project);
        roleService.ensureDefaults(project.id(), userId, members);
        return view(project);
    }

    public ProjectView updateMembers(String userId, String projectId, Set<String> memberIds) {
        Project project = store.findProject(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
        if (!project.ownerId().equals(userId)) {
            throw new ForbiddenException("只有项目负责人可以管理成员");
        }
        Set<String> members = new LinkedHashSet<>(memberIds);
        members.add(project.ownerId());
        Project updated = new Project(project.id(), project.name(), project.description(), project.ownerId(),
                Set.copyOf(members), project.status());
        project.memberIds().stream().filter(id -> !members.contains(id))
                .forEach(id -> roleService.removeMember(projectId, id));
        store.addProject(updated);
        roleService.synchronizeMembers(projectId, members);
        return view(updated);
    }

    private ProjectView view(Project project) {
        return new ProjectView(project.id(), project.name(), project.description(), project.ownerId(),
                project.status(), project.memberIds(), roleService.rolesForProject(project),
                (int) store.documentCount(project.id()),
                (int) store.decisionCount(project.id()));
    }
}
