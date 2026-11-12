package com.jaymrli.teamknowledge.repository;

import com.jaymrli.teamknowledge.domain.Domain.ProjectRole;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProjectRoleRepository {
    private final Map<String, ProjectRole> roles = new ConcurrentHashMap<>();

    public Optional<ProjectRole> find(String projectId, String userId) {
        return Optional.ofNullable(roles.get(key(projectId, userId)));
    }

    public void put(String projectId, String userId, ProjectRole role) {
        roles.put(key(projectId, userId), role);
    }

    public void putIfAbsent(String projectId, String userId, ProjectRole role) {
        roles.putIfAbsent(key(projectId, userId), role);
    }

    public void remove(String projectId, String userId) {
        roles.remove(key(projectId, userId));
    }

    public Map<String, ProjectRole> all() {
        return Map.copyOf(roles);
    }

    public void replaceAll(Map<String, ProjectRole> restored) {
        roles.clear();
        if (restored != null) roles.putAll(restored);
    }

    private String key(String projectId, String userId) {
        return projectId + ":" + userId;
    }
}
