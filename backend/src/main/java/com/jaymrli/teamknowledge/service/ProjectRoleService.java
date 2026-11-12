package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectRole;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.repository.ProjectRoleRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ProjectRoleService {
    private final KnowledgeStore store;
    private final ProjectRoleRepository roles;
    private final AccessControlService accessControl;

    public ProjectRoleService(KnowledgeStore store, ProjectRoleRepository roles, AccessControlService accessControl) {
        this.store = store;
        this.roles = roles;
        this.accessControl = accessControl;
    }

    public ProjectRole role(String userId, String projectId) {
        Project project = accessControl.requireAccessibleProject(userId, projectId);
        if (project.ownerId().equals(userId)) return ProjectRole.OWNER;
        return roles.find(projectId, userId).orElse(ProjectRole.EDITOR);
    }

    public void requireEditor(String userId, String projectId) {
        if (role(userId, projectId) == ProjectRole.VIEWER) {
            throw new ForbiddenException("当前角色为只读，不能修改项目知识");
        }
    }

    public void requireOwner(String userId, String projectId) {
        if (role(userId, projectId) != ProjectRole.OWNER) {
            throw new ForbiddenException("只有项目负责人可以执行该操作");
        }
    }

    public java.util.Map<String, String> rolesForProject(Project project) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        project.memberIds().stream().sorted().forEach(id -> {
            ProjectRole role = project.ownerId().equals(id) ? ProjectRole.OWNER
                    : roles.find(project.id(), id).orElse(ProjectRole.EDITOR);
            result.put(id, role.name());
        });
        return result;
    }

    public ProjectRole setRole(String actorId, String projectId, String memberId, String roleName) {
        requireOwner(actorId, projectId);
        Project project = store.findProject(projectId).orElseThrow();
        if (project.ownerId().equals(memberId)) {
            throw new IllegalArgumentException("不能修改项目负责人角色");
        }
        if (!project.memberIds().contains(memberId)) {
            throw new IllegalArgumentException("目标用户不是项目成员");
        }
        ProjectRole role;
        try {
            role = ProjectRole.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的项目角色: " + roleName);
        }
        if (role == ProjectRole.OWNER) throw new IllegalArgumentException("项目负责人只能通过成员转移产生");
        roles.put(projectId, memberId, role);
        return role;
    }

    public void ensureDefaults(String projectId, String ownerId, java.util.Set<String> memberIds) {
        roles.put(projectId, ownerId, ProjectRole.OWNER);
        memberIds.stream().filter(id -> !id.equals(ownerId))
                .forEach(id -> roles.putIfAbsent(projectId, id, ProjectRole.EDITOR));
    }

    public void synchronizeMembers(String projectId, java.util.Set<String> memberIds) {
        memberIds.forEach(id -> roles.putIfAbsent(projectId, id, ProjectRole.EDITOR));
    }

    public void removeMember(String projectId, String userId) {
        roles.remove(projectId, userId);
    }
}
