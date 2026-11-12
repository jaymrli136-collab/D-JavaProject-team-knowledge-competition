package com.jaymrli.teamknowledge.repository;

import com.jaymrli.teamknowledge.domain.Domain.ProjectMemory;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProjectMemoryRepository {
    private final Map<String, ProjectMemory> memories = new ConcurrentHashMap<>();

    public ProjectMemory save(ProjectMemory memory) {
        memories.put(memory.id(), memory);
        return memory;
    }

    public Optional<ProjectMemory> find(String id) {
        return Optional.ofNullable(memories.get(id));
    }

    public List<ProjectMemory> forProject(String projectId) {
        return memories.values().stream()
                .filter(item -> item.projectId().equals(projectId))
                .sorted(Comparator.comparing(ProjectMemory::createdAt).reversed())
                .toList();
    }

    public List<ProjectMemory> all() {
        return memories.values().stream()
                .sorted(Comparator.comparing(ProjectMemory::createdAt).reversed())
                .toList();
    }
}
