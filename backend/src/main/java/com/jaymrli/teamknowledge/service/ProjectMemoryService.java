package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.MemoryProposalRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryView;
import com.jaymrli.teamknowledge.domain.Domain.AgentRun;
import com.jaymrli.teamknowledge.domain.Domain.MemoryStatus;
import com.jaymrli.teamknowledge.domain.Domain.MemoryType;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectMemory;
import com.jaymrli.teamknowledge.domain.Domain.RunStatus;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.repository.ProjectMemoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectMemoryService {
    private final ProjectMemoryRepository memoryRepository;
    private final KnowledgeStore knowledgeStore;
    private final AccessControlService accessControl;
    private final KnowledgeAgentHarness harness;

    public ProjectMemoryService(ProjectMemoryRepository memoryRepository, KnowledgeStore knowledgeStore,
                                AccessControlService accessControl, KnowledgeAgentHarness harness) {
        this.memoryRepository = memoryRepository;
        this.knowledgeStore = knowledgeStore;
        this.accessControl = accessControl;
        this.harness = harness;
    }

    public MemoryView propose(String userId, MemoryProposalRequest request) {
        accessControl.requireAccessibleProject(userId, request.projectId());
        AgentRun run = harness.getRun(userId, request.sourceRunId());
        if (run.status() != RunStatus.ANSWERED) {
            throw new IllegalArgumentException("只有已完成且带证据的回答可以沉淀为记忆");
        }
        Set<String> runEvidenceIds = run.evidence().stream()
                .map(item -> item.evidenceId()).collect(java.util.stream.Collectors.toSet());
        boolean allEvidenceValid = request.evidenceIds().stream().allMatch(runEvidenceIds::contains);
        boolean allEvidenceInProject = request.evidenceIds().stream().allMatch(id -> run.evidence().stream()
                .anyMatch(item -> item.evidenceId().equals(id) && item.projectId().equals(request.projectId())));
        if (!allEvidenceValid || !allEvidenceInProject) {
            throw new IllegalArgumentException("记忆来源必须来自同一次运行且属于目标项目");
        }
        MemoryType type;
        try {
            type = MemoryType.valueOf(request.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的记忆类型: " + request.type());
        }
        double confidence = run.evidence().stream()
                .filter(item -> request.evidenceIds().contains(item.evidenceId()))
                .mapToDouble(item -> item.finalScore()).average().orElse(0.5);
        ProjectMemory memory = new ProjectMemory(
                "mem_" + UUID.randomUUID(), request.projectId(), type,
                request.title().trim(), request.content().trim(), MemoryStatus.PROPOSED,
                Math.round(confidence * 10000.0) / 10000.0,
                run.runId(), List.copyOf(new LinkedHashSet<>(request.evidenceIds())),
                userId, Instant.now(), null, null, null, null
        );
        return toView(memoryRepository.save(memory));
    }

    public MemoryView confirm(String userId, String memoryId) {
        ProjectMemory memory = requireMemory(userId, memoryId);
        if (memory.status() == MemoryStatus.RETIRED) {
            throw new IllegalArgumentException("已撤回的记忆不能直接确认");
        }
        if (memory.status() == MemoryStatus.ACTIVE) return toView(memory);
        ProjectMemory updated = new ProjectMemory(memory.id(), memory.projectId(), memory.type(),
                memory.title(), memory.content(), MemoryStatus.ACTIVE, memory.confidence(),
                memory.sourceRunId(), memory.sourceEvidenceIds(), memory.createdBy(), memory.createdAt(),
                userId, Instant.now(), null, null);
        return toView(memoryRepository.save(updated));
    }

    public MemoryView retire(String userId, String memoryId) {
        ProjectMemory memory = requireMemory(userId, memoryId);
        if (memory.status() == MemoryStatus.RETIRED) return toView(memory);
        ProjectMemory updated = new ProjectMemory(memory.id(), memory.projectId(), memory.type(),
                memory.title(), memory.content(), MemoryStatus.RETIRED, memory.confidence(),
                memory.sourceRunId(), memory.sourceEvidenceIds(), memory.createdBy(), memory.createdAt(),
                memory.confirmedBy(), memory.confirmedAt(), userId, Instant.now());
        return toView(memoryRepository.save(updated));
    }

    public List<MemoryView> listVisible(String userId, String projectId) {
        Set<String> visible = new LinkedHashSet<>();
        if (projectId == null || projectId.isBlank()) {
            accessControl.visibleProjects(userId).forEach(project -> visible.add(project.id()));
        } else {
            accessControl.requireAccessibleProject(userId, projectId);
            visible.add(projectId);
        }
        return memoryRepository.all().stream()
                .filter(item -> visible.contains(item.projectId()))
                .map(this::toView)
                .toList();
    }

    public List<ProjectMemory> activeForProjects(Set<String> projectIds) {
        return memoryRepository.all().stream()
                .filter(item -> projectIds.contains(item.projectId()))
                .filter(item -> item.status() == MemoryStatus.ACTIVE)
                .toList();
    }

    private ProjectMemory requireMemory(String userId, String memoryId) {
        ProjectMemory memory = memoryRepository.find(memoryId)
                .orElseThrow(() -> new NotFoundException("记忆不存在: " + memoryId));
        accessControl.requireAccessibleProject(userId, memory.projectId());
        return memory;
    }

    private MemoryView toView(ProjectMemory memory) {
        Project project = knowledgeStore.findProject(memory.projectId()).orElseThrow();
        return new MemoryView(memory.id(), memory.projectId(), project.name(), memory.type().name(),
                memory.title(), memory.content(), memory.status().name(), memory.confidence(),
                memory.sourceRunId(), memory.sourceEvidenceIds(), memory.createdBy(), memory.createdAt(),
                memory.confirmedBy(), memory.confirmedAt(), memory.retiredBy(), memory.retiredAt());
    }
}
